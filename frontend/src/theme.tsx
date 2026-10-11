import { useCallback, useSyncExternalStore } from 'react';
import type { MouseEvent } from 'react';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'campusconnect-theme';
const listeners = new Set<() => void>();

function systemTheme(): Theme {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    return null;
  }
}

/** The theme in force: the one on <html> (set before first paint by index.html), else stored, else the OS. */
export function currentTheme(): Theme {
  const attr = typeof document !== 'undefined' ? document.documentElement.dataset.theme : undefined;
  return attr === 'light' || attr === 'dark' ? attr : (storedTheme() ?? systemTheme());
}

function apply(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // private mode: the choice simply is not remembered
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Current theme plus a setter; components re-render when it changes (e.g. canvases redraw). */
export function useTheme() {
  const theme = useSyncExternalStore(subscribe, currentTheme, () => 'light' as Theme);
  const setTheme = useCallback((next: Theme) => apply(next), []);
  return { theme, setTheme };
}

/** Reads a CSS custom property of the active theme, for canvas drawing. */
export function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

type ViewTransitionDocument = Document & { startViewTransition?: (update: () => void) => unknown };

/** Sun / moon switch; the new theme spreads out in a circle from the button where the browser supports it. */
export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const next: Theme = theme === 'dark' ? 'light' : 'dark';

  const toggle = (event: MouseEvent<HTMLButtonElement>) => {
    const doc = document as ViewTransitionDocument;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!doc.startViewTransition || reduced) {
      setTheme(next);
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const root = document.documentElement;
    root.style.setProperty('--reveal-x', `${rect.left + rect.width / 2}px`);
    root.style.setProperty('--reveal-y', `${rect.top + rect.height / 2}px`);
    doc.startViewTransition(() => setTheme(next));
  };

  return (
    <button
      type="button"
      className={`theme-toggle ${theme}`}
      onClick={toggle}
      aria-label={`Switch to ${next} theme`}
      title={`Switch to ${next} theme`}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <mask id="theme-moon-cut">
          <rect width="24" height="24" fill="white" />
          <circle className="moon-cut" cx="17" cy="7" r="6" fill="black" />
        </mask>
        <circle className="sun-core" cx="12" cy="12" r="5" mask="url(#theme-moon-cut)" fill="currentColor" />
        <g className="sun-rays" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M12 1.5v2.5M12 20v2.5M1.5 12H4M20 12h2.5M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8" />
        </g>
      </svg>
    </button>
  );
}
