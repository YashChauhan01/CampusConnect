import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { setAccessToken } from '../../api/client';
import { AuthProvider } from '../../auth/AuthContext';
import { mockApi } from '../../test/mockApi';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from './AuthPages';

const PROFILE = { id: '1', fullName: 'Asha Rao', email: 'asha@college.edu', bio: null, admin: false, skills: [], subjects: [] };

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify" element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/" element={<p>home</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('auth pages', () => {
  it('logs in and navigates home', async () => {
    setAccessToken(null);
    const { calls } = mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/login': { body: { accessToken: 't', expiresInSeconds: 900 } },
      'GET /students/me': { body: PROFILE },
    });
    renderAt('/login');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('College email'), 'asha@college.edu');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('home')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'POST /auth/login')?.body).toEqual({
      email: 'asha@college.edu',
      password: 'correct-horse-battery',
    });
  });

  it('shows the server error for bad credentials', async () => {
    mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/login': { status: 401, body: { error: 'Invalid email or password', code: 'UNAUTHORIZED' } },
    });
    renderAt('/login');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('College email'), 'a@college.edu');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Log in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });

  it('offers to resend the verification email for unverified accounts', async () => {
    const { calls } = mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/login': { status: 403, body: { error: 'Please verify', code: 'EMAIL_NOT_VERIFIED' } },
      'POST /auth/resend-verification': { status: 202, body: { message: 'sent' } },
    });
    renderAt('/login');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('College email'), 'a@college.edu');
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await user.click(await screen.findByRole('button', { name: /resend the verification email/i }));

    expect(await screen.findByText(/new link is on its way/i)).toBeInTheDocument();
    expect(calls.some((c) => c.key === 'POST /auth/resend-verification')).toBe(true);
  });

  it('registers and tells the user to check their inbox', async () => {
    const { calls } = mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/register': { status: 202, body: { message: 'A verification link is on its way' } },
    });
    renderAt('/register');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Full name'), 'Asha Rao');
    await user.type(screen.getByLabelText('College email'), 'asha@college.edu');
    await user.type(screen.getByLabelText(/^Password/), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Check your inbox')).toBeInTheDocument();
    expect(calls.find((c) => c.key === 'POST /auth/register')?.body).toMatchObject({ fullName: 'Asha Rao' });
  });

  it('verifies the email token from the link exactly once', async () => {
    const { calls } = mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/verify': { body: { message: 'Email verified. You can now log in.' } },
    });
    renderAt('/verify?token=abc123');

    expect(await screen.findByText(/email verified/i)).toBeInTheDocument();
    expect(calls.filter((c) => c.key === 'POST /auth/verify')).toHaveLength(1);
    expect(calls.find((c) => c.key === 'POST /auth/verify')?.body).toEqual({ token: 'abc123' });
  });

  it('reports a broken verification link', async () => {
    mockApi({ 'POST /auth/refresh': { status: 401, body: {} } });
    renderAt('/verify');
    expect(await screen.findByRole('alert')).toHaveTextContent(/incomplete/i);
  });

  it('requests a password reset link', async () => {
    mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/forgot-password': { status: 202, body: { message: 'If an account exists, a reset link is on its way' } },
    });
    renderAt('/forgot-password');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('College email'), 'asha@college.edu');
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(await screen.findByText(/reset link is on its way/i)).toBeInTheDocument();
  });

  it('sets a new password using the token from the link', async () => {
    const { calls } = mockApi({
      'POST /auth/refresh': { status: 401, body: {} },
      'POST /auth/reset-password': { body: { message: 'Password updated. You can now log in.' } },
    });
    renderAt('/reset-password?token=tok');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(/^New password/), 'a-brand-new-password');
    await user.click(screen.getByRole('button', { name: 'Update password' }));

    await waitFor(() => expect(screen.getByText('Password updated')).toBeInTheDocument());
    expect(calls.find((c) => c.key === 'POST /auth/reset-password')?.body).toEqual({
      token: 'tok',
      password: 'a-brand-new-password',
    });
  });
});
