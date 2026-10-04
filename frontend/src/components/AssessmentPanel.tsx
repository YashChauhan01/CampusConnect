import { useEffect, useRef, useState, type FormEvent } from 'react';
import { assessments } from '../api/endpoints';
import type { Assessment } from '../api/types';
import { useAction } from '../hooks';
import { Alert, Spinner, levelLabel } from './ui';

interface Props {
  kind: 'SKILL' | 'SUBJECT';
  itemId: number;
  name: string;
  onClose: () => void;
  /** Called after grading so the profile can refresh its verified badges. */
  onGraded: () => void;
}

/** A short written knowledge check for one skill or subject. */
export function AssessmentPanel({ kind, itemId, name, onClose, onGraded }: Props) {
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [answers, setAnswers] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { pending, error, run } = useAction();
  // Starting a check creates server state; React StrictMode would otherwise start it twice in development.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    assessments.start(kind, itemId).then(
      (a) => {
        setAssessment(a);
        setAnswers(a.questions.map(() => ''));
      },
      (e: Error) => setLoadError(e.message),
    );
  }, [kind, itemId]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!assessment) return;
    const graded = await run(() => assessments.submit(assessment.id, answers));
    if (graded) {
      setAssessment(graded);
      onGraded();
    }
  }

  return (
    <div className="assessment" role="region" aria-label={`Knowledge check for ${name}`}>
      <div className="row spread">
        <strong>Verify: {name}</strong>
        <button type="button" className="link" onClick={onClose}>
          Close
        </button>
      </div>
      {loadError && <Alert>{loadError}</Alert>}
      {!assessment && !loadError && <Spinner label="Preparing your questions…" />}

      {assessment?.status === 'PENDING' && (
        <form onSubmit={submit}>
          <p className="muted small">
            You claimed <strong>{levelLabel(assessment.claimedLevel)}</strong>. Answer in your own words — a few sentences
            or a short snippet is enough. A check can confirm or lower your level, never raise it.
          </p>
          <ol>
            {assessment.questions.map((question, i) => (
              <li key={i}>
                <label>
                  {question}
                  <textarea
                    value={answers[i] ?? ''}
                    onChange={(e) => setAnswers((cur) => cur.map((a, j) => (j === i ? e.target.value : a)))}
                    rows={3}
                    maxLength={2000}
                    aria-label={`Answer ${i + 1}`}
                  />
                </label>
              </li>
            ))}
          </ol>
          <Alert>{error}</Alert>
          <button className="button" disabled={pending}>
            {pending ? 'Grading…' : 'Submit answers'}
          </button>
        </form>
      )}

      {assessment?.status === 'GRADED' && (
        <div>
          <p className="result">
            Score <strong>{assessment.score}</strong>/100 · verified level{' '}
            <strong>{assessment.verifiedLevel ? levelLabel(assessment.verifiedLevel) : '—'}</strong>
          </p>
          {assessment.feedback && <p>{assessment.feedback}</p>}
          <p className="muted small">You can retake a check about an hour after the last one.</p>
        </div>
      )}
    </div>
  );
}
