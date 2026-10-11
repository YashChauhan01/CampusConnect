import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { AnalysisMember, TeamAnalysis } from '../api/types';
import { mockApi } from '../test/mockApi';
import { TeamSynthesisPage } from './TeamSynthesisPage';

const member = (name: string, roleId: number, fit: number, preferenceRank: number | null): AnalysisMember => ({
  studentId: `id-${name}`,
  name,
  roleId,
  fit,
  strength: 60,
  preferenceRank,
  preferences: preferenceRank ? [roleId] : [],
  skills: [{ name: roleId === 1 ? 'Java' : 'React', level: 3 }],
  fitByRole: roleId === 1 ? [92, 10] : [8, 88],
});

const analysis: TeamAnalysis = {
  hackathonId: 'h1',
  name: 'Campus Hack',
  status: 'CLOSED',
  participants: 4,
  teamCount: 2,
  roles: [
    { id: 1, name: 'Backend', keywords: ['java'], holders: 2, firstChoices: 2 },
    { id: 2, name: 'Frontend', keywords: ['react'], holders: 2, firstChoices: 1 },
  ],
  teams: [
    { number: 1, averageStrength: 61, members: [member('Asha Rao', 1, 92, 1), member('Ravi Patel', 2, 88, 1)] },
    { number: 2, averageStrength: 59, members: [member('Meera Shah', 1, 80, 1), member('Kunal Jain', 2, 40, null)] },
  ],
  hungarian: {
    averageFit: 0.75, preferenceSatisfaction: 0.75, strengthStdDev: 0.01, strengthSpread: 0.02,
    preferenceRanks: [0.75, 0, 0, 0.25], teamStrengths: [0.59, 0.61], millis: 3.2,
  },
  random: {
    averageFit: 0.4, preferenceSatisfaction: 0.38, strengthStdDev: 0.08, strengthSpread: 0.16,
    preferenceRanks: [0.38, 0, 0, 0.62], teamStrengths: [0.52, 0.68], millis: 0.1,
  },
  randomRuns: 50,
  check: { participants: 4, roles: 2, movesExamined: 4, improvingCycles: 0, totalFit: 3, passed: true },
  flow: [[2, 0], [0, 1], [0, 1]],
};

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/hackathons/h1/analysis']}>
      <Routes>
        <Route path="/hackathons/:id/analysis" element={<TeamSynthesisPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('TeamSynthesisPage', () => {
  it('compares Hungarian with random teams and shows the optimality proof', async () => {
    mockApi({ 'GET /hackathons/h1/analysis': { body: analysis } });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Team synthesis · Campus Hack' })).toBeInTheDocument();
    expect(screen.getAllByText('75%')).not.toHaveLength(0);
    expect(screen.getByText('random teams 40%')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('0 improving cycles found');
    expect(screen.getByRole('status')).toHaveTextContent('this role allocation is optimal');
    expect(screen.getByRole('img', { name: /average strength of every team/i })).toBeInTheDocument();
    expect(screen.getByText('Team 1')).toBeInTheDocument();
    expect(screen.getByText('Team 2')).toBeInTheDocument();
  });

  it('opens a participant to show skills and fit for every role', async () => {
    mockApi({ 'GET /hackathons/h1/analysis': { body: analysis } });
    renderPage();
    await userEvent.click((await screen.findAllByRole('button', { name: /Kunal Jain/ }))[0]);
    expect(await screen.findByRole('heading', { name: 'Kunal Jain' })).toBeInTheDocument();
    expect(screen.getByText(/role not ranked/)).toBeInTheDocument();
    expect(screen.getByText('No preference given')).toBeInTheDocument();
  });

  it('filters teams by participant name', async () => {
    mockApi({ 'GET /hackathons/h1/analysis': { body: analysis } });
    renderPage();
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Find a participant' }), 'meera');
    expect(screen.queryByText('Team 1')).not.toBeInTheDocument();
    expect(screen.getByText('Team 2')).toBeInTheDocument();
    expect(screen.getByText('1 of 2 teams')).toBeInTheDocument();
  });

  it('flags a failed proof', async () => {
    mockApi({
      'GET /hackathons/h1/analysis': { body: { ...analysis, check: { ...analysis.check, improvingCycles: 2, passed: false } } },
    });
    renderPage();
    expect(await screen.findByText(/could trade roles for a higher total fit/)).toHaveClass('check-fail');
  });
});
