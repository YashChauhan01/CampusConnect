import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { MatchView, PresenceView, Profile } from '../api/types';
import { DashboardPage } from '../pages/DashboardPage';
import { mockApi } from '../test/mockApi';
import { Layout, profileStrength } from './Layout';

vi.mock('../realtime/RealtimeContext', () => ({
  useRealtime: () => ({ version: () => 0, inbox: { items: [], unread: 2 }, connected: true, toasts: [], dismissToast: () => {} }),
}));

const profile: Profile = {
  id: 'me',
  fullName: 'Asha Rao',
  email: 'asha@college.edu',
  bio: null,
  admin: true,
  skills: [{ id: 1, name: 'Java', proficiency: 'ADVANCED', verifiedLevel: null }],
  subjects: [{ id: 7, name: 'DBMS', proficiency: 'BEGINNER', verifiedLevel: null }],
};
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ profile, logout: async () => {} }) }));

const proposal: MatchView = {
  id: 'm1', status: 'PROPOSED', score: 87, breakdown: { knowledge: 1, reciprocity: 1, breadth: 0.33, proximity: 1 },
  partner: { id: 'p', name: 'Ravi Patel', zone: 'Library', bio: null, email: null }, sharedSubjects: ['DBMS'],
  youAccepted: false, partnerAccepted: false, createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 300_000).toISOString(),
  direct: false,
  requestedByYou: false,
};

function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname + l.search}</p>;
}

function renderShell(path = '/') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="*" element={<Where />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  it('shows the sidebar sections, the admin links, a badge for a waiting proposal and the breadcrumb', async () => {
    mockApi({ 'GET /matching/matches/current': { body: proposal } });
    renderShell('/campus');
    const main = screen.getByRole('navigation', { name: 'Main' });
    for (const name of ['Overview', 'Campus', 'Matches', 'Hackathons', 'Profile']) {
      expect(within(main).getByRole('link', { name: new RegExp(name) })).toBeInTheDocument();
    }
    expect(within(screen.getByRole('navigation', { name: 'Admin' })).getByRole('link', { name: /Match graph/ })).toBeInTheDocument();
    expect(await screen.findByLabelText('1 proposal waiting')).toBeInTheDocument();
    expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByText('Campus')).toBeInTheDocument();
  });

  it('opens and closes the mobile menu, closing it again after navigating', async () => {
    mockApi({ 'GET /matching/matches/current': { status: 204 } });
    const user = userEvent.setup();
    renderShell('/');
    const shell = document.querySelector('.app-shell')!;
    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    expect(shell).toHaveClass('nav-open');
    await user.click(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: /Hackathons/ }));
    expect(screen.getByTestId('where')).toHaveTextContent('/hackathons');
    expect(shell).not.toHaveClass('nav-open');
  });

  it('searches campus for peers and subjects', async () => {
    mockApi({ 'GET /matching/matches/current': { status: 204 } });
    const user = userEvent.setup();
    renderShell('/');
    await user.type(screen.getByRole('searchbox', { name: /Search peers/ }), 'DBMS{Enter}');
    expect(screen.getByTestId('where')).toHaveTextContent('/campus?q=DBMS');
  });

  it('rates profile completeness and suggests the next step', () => {
    expect(profileStrength(profile)).toEqual({ percent: 27, tip: 'Add another subject to unlock better study matches.' });
    const full = { ...profile, bio: 'Hi', subjects: [...Array(3)].map((_, i) => ({ ...profile.subjects[0], id: i })),
      skills: [...Array(3)].map((_, i) => ({ ...profile.skills[0], id: i })) };
    expect(profileStrength(full)).toEqual({ percent: 100, tip: 'Your profile is complete. Nice!' });
  });
});

describe('DashboardPage', () => {
  const someone = (id: string, name: string, zoneId: number, zone: string): PresenceView => ({
    studentId: id, name, zoneId, zone, status: 'AVAILABLE', requirements: null, seeking: [], subjects: [], expiresAt: new Date(Date.now() + 600_000).toISOString(),
  });

  it('shows live campus numbers, the check-in state and recommendations', async () => {
    mockApi({
      'GET /presence/me': { body: someone('me', 'Asha Rao', 1, 'Library') },
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }, { id: 2, name: 'Cafeteria' }] },
      'GET /presence/available': { body: [someone('me', 'Asha Rao', 1, 'Library'), someone('a', 'Ravi Patel', 1, 'Library'), someone('b', 'Meera Shah', 2, 'Cafeteria')] },
      'GET /matching/matches/current': { status: 204 },
      'GET /hackathons': { body: [] },
      'GET /matching/suggestions': { body: [{ studentId: 'a', name: 'Ravi Patel', zone: 'Library', score: 91, sharedSubjects: ['DBMS'], breakdown: proposal.breakdown }] },
    });
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText('Checked in')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Find your people.' })).toBeInTheDocument();
    // Two other students on campus (the viewer is not counted), one of them in the same zone.
    expect(await screen.findByText('2')).toBeInTheDocument();
    expect(screen.getByText(/1 peer in Library/)).toBeInTheDocument();
    expect(await screen.findByText('91% match')).toBeInTheDocument();
    expect(screen.getByText('91%')).toBeInTheDocument();
  });

  it('asks the student to check in before showing recommendations', async () => {
    mockApi({
      'GET /presence/me': { status: 204 },
      'GET /presence/zones': { body: [{ id: 1, name: 'Library' }] },
      'GET /presence/available': { body: [] },
      'GET /matching/matches/current': { status: 204 },
      'GET /hackathons': { body: [] },
    });
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText('Not checked in')).toBeInTheDocument();
    expect(screen.getByText(/Check in above to see the students/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /Check in/ })).toBeEnabled());
  });
});
