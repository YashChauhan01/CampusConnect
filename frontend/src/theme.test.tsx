import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { ThemeToggle } from './theme';

describe('ThemeToggle', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.dataset.theme = 'light';
  });

  it('switches between light and dark, remembers the choice and relabels itself', async () => {
    render(<ThemeToggle />);
    const button = screen.getByRole('button', { name: 'Switch to dark theme' });

    await userEvent.click(button);
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('campusconnect-theme')).toBe('dark');
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Switch to light theme' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('campusconnect-theme')).toBe('light');
  });

  it('starts from the theme already applied to the page', () => {
    document.documentElement.dataset.theme = 'dark';
    render(<ThemeToggle />);
    expect(screen.getByRole('button', { name: 'Switch to light theme' })).toBeInTheDocument();
  });
});
