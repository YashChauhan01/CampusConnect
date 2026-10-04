import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useRealtime } from '../realtime/RealtimeContext';
import { NotificationBell } from './NotificationBell';

function Toasts() {
  const { toasts, dismissToast } = useRealtime();
  const navigate = useNavigate();
  return (
    <div className="toasts" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="toast">
          <button
            type="button"
            className="toast-body"
            onClick={() => {
              dismissToast(t.id);
              if (t.link) navigate(t.link);
            }}
          >
            <strong>{t.title}</strong>
            <span>{t.body}</span>
          </button>
          <button type="button" className="icon-button" aria-label="Dismiss" onClick={() => dismissToast(t.id)}>
            ×
          </button>
        </div>
      ))}
    </div>
  );
}

/** Shell for authenticated pages: navigation, notification bell, live toasts. */
export function Layout() {
  const { profile, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="logo" aria-hidden="true" />
          CampusConnect
        </Link>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Home
          </NavLink>
          <NavLink to="/campus">Campus</NavLink>
          <NavLink to="/matches">Matches</NavLink>
          <NavLink to="/hackathons">Hackathons</NavLink>
          <NavLink to="/profile">Profile</NavLink>
          {profile?.admin && <NavLink to="/admin/zones">Zones</NavLink>}
        </nav>
        <div className="topbar-right">
          <NotificationBell />
          <span className="who" title={profile?.email}>
            {profile?.fullName}
          </span>
          <button
            type="button"
            className="button secondary"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
          >
            Log out
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      <Toasts />
    </>
  );
}
