import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { auth } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { Alert, Field } from '../../components/ui';
import { messageOf, useAction } from '../../hooks';

function AuthShell({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link to="/login" className="brand">
          <span className="logo" aria-hidden="true" />
          CampusConnect
        </Link>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
        {children}
      </div>
    </div>
  );
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [unverified, setUnverified] = useState(false);
  const [resent, setResent] = useState(false);
  const { pending, error, run } = useAction();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setUnverified(false);
    setResent(false);
    // Failures are captured by useAction and rendered as the form error.
    await run(async () => {
      try {
        await login(email, password);
      } catch (e) {
        if (e instanceof ApiError && e.code === 'EMAIL_NOT_VERIFIED') setUnverified(true);
        throw e;
      }
      navigate('/');
    });
  }

  return (
    <AuthShell title="Welcome back" subtitle="Log in with your college email.">
      <form onSubmit={submit}>
        <Field label="College email">
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Field label="Password">
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </Field>
        <Alert>{error}</Alert>
        {unverified && !resent && (
          <button
            type="button"
            className="link"
            onClick={async () => {
              await auth.resendVerification(email);
              setResent(true);
            }}
          >
            Resend the verification email
          </button>
        )}
        {resent && <Alert kind="success">If the account needs verification, a new link is on its way.</Alert>}
        <button className="button" disabled={pending}>
          {pending ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="auth-links">
        <Link to="/forgot-password">Forgot password?</Link>
        <Link to="/register">Create an account</Link>
      </p>
    </AuthShell>
  );
}

export function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [done, setDone] = useState<string | null>(null);
  const { pending, error, run } = useAction();

  async function submit(event: FormEvent) {
    event.preventDefault();
    const result = await run(() => auth.register(fullName, email, password));
    if (result) setDone(result.message);
  }

  if (done) {
    return (
      <AuthShell title="Check your inbox">
        <Alert kind="success">{done}</Alert>
        <p className="muted">
          Open the link we sent to <strong>{email}</strong> to activate your account. It expires in 30 minutes.
        </p>
        <Link to="/login">Back to log in</Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Create your account" subtitle="Only verified college emails can join.">
      <form onSubmit={submit}>
        <Field label="Full name">
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={120} required />
        </Field>
        <Field label="College email">
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Field label="Password" hint="At least 10 characters.">
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={10}
            maxLength={128}
            required
          />
        </Field>
        <Alert>{error}</Alert>
        <button className="button" disabled={pending}>
          {pending ? 'Creating…' : 'Create account'}
        </button>
      </form>
      <p className="auth-links">
        <Link to="/login">I already have an account</Link>
      </p>
    </AuthShell>
  );
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [state, setState] = useState<{ kind: 'working' | 'ok' | 'error'; message: string }>({
    kind: 'working',
    message: 'Checking your verification link…',
  });
  // Verification tokens are single use; React StrictMode runs effects twice in development.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!token) {
      setState({ kind: 'error', message: 'This verification link is incomplete.' });
      return;
    }
    auth.verify(token).then(
      (r) => setState({ kind: 'ok', message: r.message }),
      (e) => setState({ kind: 'error', message: messageOf(e) }),
    );
  }, [token]);

  return (
    <AuthShell title="Verify your email">
      {state.kind === 'working' ? (
        <p className="muted" role="status">
          {state.message}
        </p>
      ) : (
        <Alert kind={state.kind === 'ok' ? 'success' : 'error'}>{state.message}</Alert>
      )}
      <Link className="button" to="/login">
        Go to log in
      </Link>
    </AuthShell>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const { pending, error, run } = useAction();

  async function submit(event: FormEvent) {
    event.preventDefault();
    const result = await run(() => auth.forgotPassword(email));
    if (result) setMessage(result.message);
  }

  return (
    <AuthShell title="Reset your password" subtitle="We will email you a link if the account exists.">
      <form onSubmit={submit}>
        <Field label="College email">
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Alert>{error}</Alert>
        {message && <Alert kind="success">{message}</Alert>}
        <button className="button" disabled={pending}>
          Send reset link
        </button>
      </form>
      <p className="auth-links">
        <Link to="/login">Back to log in</Link>
      </p>
    </AuthShell>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [done, setDone] = useState<string | null>(null);
  const { pending, error, run } = useAction();

  async function submit(event: FormEvent) {
    event.preventDefault();
    const result = await run(() => auth.resetPassword(token, password));
    if (result) setDone(result.message);
  }

  if (done) {
    return (
      <AuthShell title="Password updated">
        <Alert kind="success">{done}</Alert>
        <Link className="button" to="/login">
          Log in
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Choose a new password">
      <form onSubmit={submit}>
        <Field label="New password" hint="At least 10 characters.">
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={10}
            maxLength={128}
            required
          />
        </Field>
        <Alert>{token ? error : 'This reset link is incomplete.'}</Alert>
        <button className="button" disabled={pending || !token}>
          Update password
        </button>
      </form>
    </AuthShell>
  );
}
