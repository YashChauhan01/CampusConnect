import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { MatchView, Profile } from '../api/types';
import { mockApi } from '../test/mockApi';
import { MatchesPage } from './MatchesPage';
import { NewHackathonPage } from './HackathonsPage';
import { ProfilePage } from './ProfilePage';

// Pages only need the change counters from the live connection; the socket itself is covered by backend tests.
vi.mock('../realtime/RealtimeContext', () => ({
  useRealtime: () => ({ version: () => 0, inbox: { items: [], unread: 0 }, connected: true }),
}));

const profile: Profile = {
  id: '1',
  fullName: 'Asha Rao',
  email: 'asha@college.edu',
  bio: null,
  skills: [],
  subjects: [{ id: 7, name: 'DBMS', proficiency: 'BEGINNER' }],
};

let currentProfile = profile;
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ profile: currentProfile, setProfile: (p: Profile) => (currentProfile = p) }),
}));

const proposed: MatchView = {
  id: 'm1',
  status: 'PROPOSED',
  score: 87,
  breakdown: { knowledge: 1, reciprocity: 1, breadth: 0.33, proximity: 1 },
  partner: { id: 'p', name: 'Ravi Patel', zone: 'Library', bio: null, email: null },
  sharedSubjects: ['DSA'],
  youAccepted: false,
  partnerAccepted: false,
  createdAt: new Date().toISOString(),
  expiresAt: new Date(Date.now() + 300_000).toISOString(),
};

describe('ProfilePage', () => {
  it('lists existing subjects and adds a new skill with its proficiency', async () => {
    currentProfile = profile;
    const { calls } = mockApi({
      'GET /subjects?q=D': { body: [] },
      'GET /skills?q=J': { body: [] },
      'GET /skills?q=Ja': { body: [] },
      'GET /skills?q=Jav': { body: [] },
      'GET /skills?q=Java': { body: ['Java'] },
      'POST /students/me/skills': (body) => ({
        body: { ...profile, skills: [{ id: 1, ...(body as object) }] },
      }),
    });
    render(<ProfilePage />);
    const user = userEvent.setup();

    expect(screen.getByText('DBMS')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Skills name'), 'Java');
    await user.selectOptions(screen.getByLabelText('Skills proficiency'), 'ADVANCED');
    await user.click(within(screen.getByRole('heading', { name: 'Skills' }).closest('section')!).getByRole('button', { name: 'Add' }));

    expect(calls.find((c) => c.key === 'POST /students/me/skills')?.body).toEqual({ name: 'Java', proficiency: 'ADVANCED' });
  });
});

describe('MatchesPage', () => {
  it('shows a proposal with its reasons and lets the student accept', async () => {
    const { calls } = mockApi({
      'GET /matching/matches/current': { body: proposed },
      'GET /matching/matches': { body: [proposed] },
      'POST /matching/matches/m1/accept': { body: { ...proposed, youAccepted: true } },
    });
    render(
      <MemoryRouter>
        <MatchesPage />
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    expect(await screen.findByText('Ravi Patel', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByLabelText('87% compatible')).toBeInTheDocument();
    expect(screen.getByText('DSA')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Why you were matched' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Accept' }));

    expect(calls.some((c) => c.key === 'POST /matching/matches/m1/accept')).toBe(true);
  });

  it('reveals contact details once the match is confirmed', async () => {
    const confirmed = { ...proposed, status: 'ACCEPTED' as const, youAccepted: true, partnerAccepted: true,
      partner: { ...proposed.partner, email: 'ravi@college.edu' } };
    mockApi({
      'GET /matching/matches/current': { body: confirmed },
      'GET /matching/matches': { body: [confirmed] },
    });
    render(
      <MemoryRouter>
        <MatchesPage />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('link', { name: 'ravi@college.edu' })).toHaveAttribute('href', 'mailto:ravi@college.edu');
  });

  it('invites the student to check in when there is no match', async () => {
    mockApi({ 'GET /matching/matches/current': { status: 204 }, 'GET /matching/matches': { body: [] } });
    render(
      <MemoryRouter>
        <MatchesPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/no active match/i)).toBeInTheDocument();
  });
});

describe('NewHackathonPage', () => {
  it('submits roles with parsed keywords and opens the created hackathon', async () => {
    const { calls } = mockApi({
      'POST /hackathons': {
        status: 201,
        body: { summary: { id: 'h1' }, myPreferences: [], participants: [] },
      },
    });
    render(
      <MemoryRouter initialEntries={['/hackathons/new']}>
        <Routes>
          <Route path="/hackathons/new" element={<NewHackathonPage />} />
          <Route path="/hackathons/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Name'), 'Campus Hack');
    await user.click(screen.getByRole('button', { name: 'Create hackathon' }));

    expect(await screen.findByText('created')).toBeInTheDocument();
    const body = calls.find((c) => c.key === 'POST /hackathons')?.body as { name: string; roles: { name: string; keywords: string[] }[] };
    expect(body.name).toBe('Campus Hack');
    expect(body.roles[0]).toEqual({ name: 'Backend', keywords: ['java', 'spring', 'sql', 'node'] });
    expect(body.roles).toHaveLength(3);
  });

  it('keeps at least two roles', async () => {
    mockApi({});
    render(
      <MemoryRouter>
        <NewHackathonPage />
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Remove role 3' }));
    expect(screen.getByRole('button', { name: 'Remove role 1' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Remove role 2' })).toBeDisabled();
  });
});
