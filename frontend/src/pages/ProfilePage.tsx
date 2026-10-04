import { useEffect, useId, useState, type FormEvent } from 'react';
import { assessments, profile as profileApi } from '../api/endpoints';
import type { Item, Profile, Proficiency } from '../api/types';
import { PROFICIENCIES } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AssessmentPanel } from '../components/AssessmentPanel';
import { Alert, EmptyState, Field, LevelPill, PageHeader, levelLabel } from '../components/ui';
import { useAction, useLoad } from '../hooks';

interface ItemEditorProps {
  kind: 'SKILL' | 'SUBJECT';
  /** Whether the server has AI skill verification switched on. */
  aiEnabled: boolean;
  /** Re-reads the profile after a check changed a verified level. */
  onGraded: () => void;
  title: string;
  hint: string;
  placeholder: string;
  items: Item[];
  suggest: (query: string) => Promise<string[]>;
  onSave: (name: string, level: Proficiency) => Promise<void>;
  onRemove: (id: number) => Promise<void>;
}

/** Add / re-level / remove list of named things with a proficiency (used for skills and subjects). */
function ItemEditor({
  kind,
  aiEnabled,
  onGraded,
  title,
  hint,
  placeholder,
  items,
  suggest,
  onSave,
  onRemove,
}: ItemEditorProps) {
  const [assessing, setAssessing] = useState<Item | null>(null);
  const [name, setName] = useState('');
  const [level, setLevel] = useState<Proficiency>('INTERMEDIATE');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const { pending, error, run } = useAction();
  const listId = useId();

  useEffect(() => {
    const query = name.trim();
    if (query.length < 1) {
      setSuggestions([]);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      suggest(query).then(
        (names) => !cancelled && setSuggestions(names),
        () => undefined,
      );
    }, 200);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [name, suggest]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const saved = await run(async () => {
      await onSave(name.trim(), level);
      return true;
    });
    if (saved) setName('');
  }

  return (
    <section className="card">
      <h2>{title}</h2>
      <p className="muted">{hint}</p>
      {items.length === 0 ? (
        <EmptyState>Nothing added yet.</EmptyState>
      ) : (
        <ul className="chips">
          {items.map((item) => (
            <li key={item.id}>
              <LevelPill name={item.name} level={item.proficiency} verifiedLevel={item.verifiedLevel} />
              {aiEnabled && (
                <button type="button" className="link small" onClick={() => setAssessing(item)}>
                  {item.verifiedLevel ? 'Retake' : 'Verify'}
                </button>
              )}
              <button
                type="button"
                className="icon-button"
                aria-label={`Remove ${item.name}`}
                onClick={() => void run(() => onRemove(item.id))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {assessing && (
        <AssessmentPanel
          key={assessing.id}
          kind={kind}
          itemId={assessing.id}
          name={assessing.name}
          onClose={() => setAssessing(null)}
          onGraded={onGraded}
        />
      )}
      <form className="inline-form" onSubmit={submit}>
        <input
          aria-label={`${title} name`}
          placeholder={placeholder}
          value={name}
          onChange={(e) => setName(e.target.value)}
          list={listId}
          maxLength={80}
          required
        />
        <datalist id={listId}>
          {suggestions.map((s) => (
            <option key={s} value={s} />
          ))}
        </datalist>
        <select aria-label={`${title} proficiency`} value={level} onChange={(e) => setLevel(e.target.value as Proficiency)}>
          {PROFICIENCIES.map((p) => (
            <option key={p} value={p}>
              {levelLabel(p)}
            </option>
          ))}
        </select>
        <button className="button" disabled={pending}>
          Add
        </button>
      </form>
      <Alert>{error}</Alert>
    </section>
  );
}

export function ProfilePage() {
  const { profile, setProfile } = useAuth();
  const [fullName, setFullName] = useState(profile?.fullName ?? '');
  const [bio, setBio] = useState(profile?.bio ?? '');
  const [saved, setSaved] = useState(false);
  const { pending, error, run } = useAction();
  const ai = useLoad(() => assessments.status().catch(() => ({ enabled: false, questionCount: 0 })), []);
  const aiEnabled = ai.data?.enabled ?? false;

  if (!profile) return null;

  async function apply(action: () => Promise<Profile>): Promise<void> {
    setProfile(await action());
  }

  async function saveDetails(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    const updated = await run(() => profileApi.update(fullName, bio));
    if (updated) {
      setProfile(updated);
      setSaved(true);
    }
  }

  return (
    <>
      <PageHeader
        title="Your profile"
        subtitle="Skills and subjects power both peer matching and hackathon teams. Be honest — it makes better matches."
      />
      <div className="grid-2">
        <section className="card">
          <h2>About you</h2>
          <form onSubmit={saveDetails}>
            <Field label="Full name">
              <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={120} required />
            </Field>
            <Field label="Email">
              <input value={profile.email} disabled />
            </Field>
            <Field label="Bio" hint="Shown to students you are matched with.">
              <textarea value={bio} onChange={(e) => setBio(e.target.value)} maxLength={500} rows={3} />
            </Field>
            <Alert>{error}</Alert>
            {saved && <Alert kind="success">Saved.</Alert>}
            <button className="button" disabled={pending}>
              Save
            </button>
          </form>
        </section>
        <div className="stack">
          <ItemEditor
            kind="SUBJECT"
            aiEnabled={aiEnabled}
            onGraded={() => void profileApi.me().then(setProfile)}
            title="Subjects"
            hint="Courses you study. Peer matching pairs you with students who share them."
            placeholder="Data Structures, DBMS…"
            items={profile.subjects}
            suggest={profileApi.suggestSubjects}
            onSave={(n, l) => apply(() => profileApi.saveSubject(n, l))}
            onRemove={(id) => apply(() => profileApi.removeSubject(id))}
          />
          <ItemEditor
            kind="SKILL"
            aiEnabled={aiEnabled}
            onGraded={() => void profileApi.me().then(setProfile)}
            title="Skills"
            hint="Technologies you can build with. Hackathon teams are formed from these."
            placeholder="Java, React, Figma…"
            items={profile.skills}
            suggest={profileApi.suggestSkills}
            onSave={(n, l) => apply(() => profileApi.saveSkill(n, l))}
            onRemove={(id) => apply(() => profileApi.removeSkill(id))}
          />
        </div>
      </div>
    </>
  );
}
