import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Assessment } from '../api/types';
import { mockApi } from '../test/mockApi';
import { AssessmentPanel } from './AssessmentPanel';

const pending: Assessment = {
  id: 'a1',
  kind: 'SUBJECT',
  itemId: 7,
  topic: 'DBMS',
  claimedLevel: 'ADVANCED',
  questions: ['What is normalisation?', 'Explain ACID.'],
  status: 'PENDING',
  score: null,
  verifiedLevel: null,
  feedback: null,
};

describe('AssessmentPanel', () => {
  it('shows the questions, submits the answers and reports the verified level', async () => {
    const onGraded = vi.fn();
    const { calls } = mockApi({
      'POST /assessments': { body: pending },
      'POST /assessments/a1/submit': {
        body: { ...pending, status: 'GRADED', score: 62, verifiedLevel: 'INTERMEDIATE', feedback: 'Good start.' },
      },
    });
    render(<AssessmentPanel kind="SUBJECT" itemId={7} name="DBMS" onClose={() => undefined} onGraded={onGraded} />);
    const user = userEvent.setup();

    expect(await screen.findByText('What is normalisation?')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Answer 1'), 'Removing redundancy');
    await user.type(screen.getByLabelText('Answer 2'), 'Atomicity, consistency, isolation, durability');
    await user.click(screen.getByRole('button', { name: 'Submit answers' }));

    expect(await screen.findByText(/verified level/i)).toHaveTextContent('Intermediate');
    expect(screen.getByText('Good start.')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'POST /assessments')?.body).toEqual({ kind: 'SUBJECT', itemId: 7 });
    expect(calls.find((c) => c.key === 'POST /assessments/a1/submit')?.body).toEqual({
      answers: ['Removing redundancy', 'Atomicity, consistency, isolation, durability'],
    });
    expect(onGraded).toHaveBeenCalledOnce();
  });

  it('explains why a check could not be started', async () => {
    mockApi({ 'POST /assessments': { status: 409, body: { error: 'You can retake this check in about 40 minutes', code: 'CONFLICT' } } });
    render(<AssessmentPanel kind="SKILL" itemId={1} name="Java" onClose={() => undefined} onGraded={() => undefined} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('retake this check in about 40 minutes');
  });

  it('starts the check only once', async () => {
    const { calls } = mockApi({ 'POST /assessments': { body: pending } });
    render(<AssessmentPanel kind="SUBJECT" itemId={7} name="DBMS" onClose={() => undefined} onGraded={() => undefined} />);
    await screen.findByText('What is normalisation?');
    expect(calls.filter((c) => c.key === 'POST /assessments')).toHaveLength(1);
  });
});
