import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Bell, Compass, Gauge, Home, LogOut, Map, Menu, Network, Search, Sparkles, Trophy, UserRound, Users, X, Zap,
} from 'lucide-react';
import type { Profile } from '../api/types';
import { matching } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';
import { ThemeToggle } from '../theme';
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

export function initials(name: string | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** How complete the profile is (subjects, skills, bio) and the next step that would improve it. */
export function profileStrength(profile: Profile): { percent: number; tip: string } {
  const subjects = Math.min(1, profile.subjects.length / 3);
  const skills = Math.min(1, profile.skills.length / 3);
  const bio = profile.bio?.trim() ? 1 : 0;
  const percent = Math.round(subjects * 40 + skills * 40 + bio * 20);
  const tip = profile.subjects.length < 3 ? 'Add another subject to unlock better study matches.'
    : profile.skills.length < 3 ? 'Add another skill for better hackathon roles.'
      : !bio ? 'Write a short bio so partners know you.' : 'Your profile is complete. Nice!';
  return { percent, tip };
}

const TITLES: [RegExp, string][] = [
  [/^\/$/, 'Overview'], [/^\/campus/, 'Campus'], [/^\/matches/, 'Matches'], [/^\/hackathons\/new/, 'New hackathon'],
  [/^\/hackathons\/[^/]+\/analysis/, 'Team synthesis'], [/^\/hackathons\/[^/]+/, 'Hackathon'], [/^\/hackathons/, 'Hackathons'],
  [/^\/profile/, 'Profile'], [/^\/notifications/, 'Notifications'], [/^\/admin\/graph/, 'Match graph'], [/^\/admin\/zones/, 'Zones'],
];

/** Shell for authenticated pages: sidebar navigation, top bar with search and notifications, live toasts. */
export function Layout() {
  const { profile, logout } = useAuth();
  const { version, inbox } = useRealtime();
  const navigate = useNavigate();
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const [query, setQuery] = useState('');
  const current = useLoad(() => matching.current(), [version('matches')]);

  // Close the mobile drawer whenever the route changes.
  useEffect(() => setNavOpen(false), [location.pathname]);
  useEffect(() => {
    if (!navOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setNavOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [navOpen]);

  const title = TITLES.find(([re]) => re.test(location.pathname))?.[1] ?? 'CampusConnect';
  const waiting = current.data?.status === 'PROPOSED' && !current.data.youAccepted ? 1 : 0;
  const strength = profile ? profileStrength(profile) : null;

  function search(event: FormEvent) {
    event.preventDefault();
    const q = query.trim();
    navigate(q ? `/campus?q=${encodeURIComponent(q)}` : '/campus');
  }

  const item = (to: string, label: string, Icon: typeof Home, extra?: ReactNode, end = false) => (
    <NavLink to={to} end={end}>
      <Icon aria-hidden="true" />
      {label}
      {extra}
    </NavLink>
  );

  return (
    <div className={`app-shell ${navOpen ? 'nav-open' : ''}`}>
      <aside className="sidebar" aria-label="Sidebar">
        <Link to="/" className="brand">
          <span className="logo" aria-hidden="true">
            <Sparkles />
          </span>
          <span>
            campus<em>Connect</em>
          </span>
        </Link>
        <p className="side-label">Your workspace</p>
        <nav className="side-nav" aria-label="Main">
          {item('/', 'Overview', Home, undefined, true)}
          {item('/campus', 'Campus', Compass)}
          {item('/matches', 'Matches', Users, waiting ? <span className="side-badge" aria-label="1 proposal waiting">1</span> : undefined)}
          {item('/hackathons', 'Hackathons', Trophy)}
          {item('/profile', 'Profile', UserRound)}
          {item('/notifications', 'Notifications', Bell,
            inbox.unread ? <span className="side-badge" aria-label={`${inbox.unread} unread`}>{inbox.unread > 9 ? '9+' : inbox.unread}</span> : undefined)}
        </nav>
        {profile?.admin && (
          <>
            <p className="side-label">Admin</p>
            <nav className="side-nav" aria-label="Admin">
              {item('/admin/graph', 'Match graph', Network)}
              {item('/admin/zones', 'Zones', Map)}
            </nav>
          </>
        )}
        <div className="side-spacer" />
        {strength && (
          <Link to="/profile" className="profile-meter">
            <span className="meter-head">
              <Zap aria-hidden="true" />
              <span>Profile strength</span>
              <span>{strength.percent}%</span>
            </span>
            <span className="meter-track" aria-hidden="true">
              <span style={{ width: `${strength.percent}%` }} />
            </span>
            <p>{strength.tip}</p>
          </Link>
        )}
        <div className="side-user">
          <span className="avatar-badge" aria-hidden="true">
            {initials(profile?.fullName)}
          </span>
          <span className="who-text">
            <strong title={profile?.email}>{profile?.fullName}</strong>
            <small>{profile?.admin ? 'Administrator' : 'Student'} · Online</small>
          </span>
          <button
            type="button"
            className="icon-button plain"
            aria-label="Log out"
            title="Log out"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
          >
            <LogOut />
          </button>
        </div>
      </aside>
      <div className="scrim" aria-hidden="true" onClick={() => setNavOpen(false)} />

      <div className="main-col">
        <header className="topbar">
          <button
            type="button"
            className="icon-button menu-button"
            aria-label={navOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={navOpen}
            onClick={() => setNavOpen((o) => !o)}
          >
            {navOpen ? <X /> : <Menu />}
          </button>
          <nav className="crumbs" aria-label="Breadcrumb">
            <Link to="/" aria-label="Home">
              <Gauge aria-hidden="true" />
            </Link>
            <span aria-hidden="true">/</span>
            <strong>{title}</strong>
          </nav>
          <div className="topbar-right">
            <form className="search-box" role="search" onSubmit={search}>
              <Search aria-hidden="true" />
              <input
                type="search"
                placeholder="Search peers, subjects…"
                aria-label="Search peers and subjects on campus"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </form>
            <ThemeToggle />
            <NotificationBell />
            <Link to="/profile" className="avatar-badge" aria-label="Your profile" title={profile?.fullName}>
              {initials(profile?.fullName)}
            </Link>
          </div>
        </header>
        <main>
          {/* Keyed by path so every navigation replays the entrance animation. */}
          <div className="page" key={location.pathname}>
            <Outlet />
          </div>
        </main>
      </div>
      <Toasts />
    </div>
  );
}
