import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { Spinner } from './components/ui';
import { RealtimeProvider } from './realtime/RealtimeContext';
import { CampusPage } from './pages/CampusPage';
import { DashboardPage } from './pages/DashboardPage';
import { HackathonDetailPage } from './pages/HackathonDetailPage';
import { HackathonsPage, NewHackathonPage } from './pages/HackathonsPage';
import { MatchesPage } from './pages/MatchesPage';
import { ProfilePage } from './pages/ProfilePage';
import {
  ForgotPasswordPage,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from './pages/auth/AuthPages';

/** Gate for authenticated routes; mounts the live connection only while logged in. */
function RequireAuth() {
  const { status } = useAuth();
  if (status === 'loading') return <Spinner label="Loading your session…" />;
  if (status === 'anonymous') return <Navigate to="/login" replace />;
  return (
    <RealtimeProvider>
      <Outlet />
    </RealtimeProvider>
  );
}

/** Login / register pages bounce logged-in users to the app. */
function PublicOnly() {
  const { status } = useAuth();
  if (status === 'loading') return <Spinner label="Loading your session…" />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}

export function App() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      </Route>
      {/* Reachable whether or not a session exists: the links come from emails. */}
      <Route path="/verify" element={<VerifyEmailPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="/campus" element={<CampusPage />} />
          <Route path="/matches" element={<MatchesPage />} />
          <Route path="/hackathons" element={<HackathonsPage />} />
          <Route path="/hackathons/new" element={<NewHackathonPage />} />
          <Route path="/hackathons/:id" element={<HackathonDetailPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
