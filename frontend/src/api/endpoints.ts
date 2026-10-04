import { api, authApi } from './client';
import type {
  CheckInRequest,
  CreateHackathonRequest,
  HackathonDetail,
  HackathonSummary,
  Inbox,
  MatchView,
  PresenceView,
  Profile,
  Proficiency,
  Suggestion,
  TeamsView,
  Zone,
} from './types';

interface Message {
  message: string;
}

export const auth = {
  register: (fullName: string, email: string, password: string) =>
    authApi.post<Message>('/auth/register', { fullName, email, password }),
  verify: (token: string) => authApi.post<Message>('/auth/verify', { token }),
  resendVerification: (email: string) => authApi.post<Message>('/auth/resend-verification', { email }),
  login: (email: string, password: string) =>
    authApi.post<{ accessToken: string; expiresInSeconds: number }>('/auth/login', { email, password }),
  logout: () => authApi.post<Message>('/auth/logout'),
  forgotPassword: (email: string) => authApi.post<Message>('/auth/forgot-password', { email }),
  resetPassword: (token: string, password: string) =>
    authApi.post<Message>('/auth/reset-password', { token, password }),
};

export const profile = {
  me: () => api.get<Profile>('/students/me'),
  update: (fullName: string, bio: string) => api.put<Profile>('/students/me', { fullName, bio }),
  saveSkill: (name: string, proficiency: Proficiency) =>
    api.post<Profile>('/students/me/skills', { name, proficiency }),
  removeSkill: (id: number) => api.delete<Profile>(`/students/me/skills/${id}`),
  saveSubject: (name: string, proficiency: Proficiency) =>
    api.post<Profile>('/students/me/subjects', { name, proficiency }),
  removeSubject: (id: number) => api.delete<Profile>(`/students/me/subjects/${id}`),
  suggestSkills: (q: string) => api.get<string[]>(`/skills?q=${encodeURIComponent(q)}`),
  suggestSubjects: (q: string) => api.get<string[]>(`/subjects?q=${encodeURIComponent(q)}`),
};

export const presence = {
  zones: () => api.get<Zone[]>('/presence/zones'),
  mine: () => api.get<PresenceView | undefined>('/presence/me'),
  available: (zoneId?: number) =>
    api.get<PresenceView[]>(zoneId ? `/presence/available?zoneId=${zoneId}` : '/presence/available'),
  checkIn: (request: CheckInRequest) => api.post<PresenceView>('/presence/check-in', request),
  checkOut: () => api.post<void>('/presence/check-out'),
};

export const matching = {
  current: () => api.get<MatchView | undefined>('/matching/matches/current'),
  history: () => api.get<MatchView[]>('/matching/matches'),
  suggestions: () => api.get<Suggestion[]>('/matching/suggestions'),
  find: () => api.post<MatchView | undefined>('/matching/find'),
  accept: (id: string) => api.post<MatchView>(`/matching/matches/${id}/accept`),
  decline: (id: string) => api.post<MatchView>(`/matching/matches/${id}/decline`),
  complete: (id: string) => api.post<MatchView>(`/matching/matches/${id}/complete`),
};

export const hackathons = {
  list: () => api.get<HackathonSummary[]>('/hackathons'),
  create: (request: CreateHackathonRequest) => api.post<HackathonDetail>('/hackathons', request),
  detail: (id: string) => api.get<HackathonDetail>(`/hackathons/${id}`),
  register: (id: string, rolePreferences: number[]) =>
    api.put<HackathonDetail>(`/hackathons/${id}/registration`, { rolePreferences }),
  withdraw: (id: string) => api.delete<HackathonDetail>(`/hackathons/${id}/registration`),
  close: (id: string) => api.post<HackathonDetail>(`/hackathons/${id}/close`),
  reopen: (id: string) => api.post<HackathonDetail>(`/hackathons/${id}/reopen`),
  synthesize: (id: string) => api.post<TeamsView>(`/hackathons/${id}/synthesize`),
  publish: (id: string) => api.post<TeamsView>(`/hackathons/${id}/publish`),
  teams: (id: string) => api.get<TeamsView>(`/hackathons/${id}/teams`),
};

export const notifications = {
  inbox: () => api.get<Inbox>('/notifications'),
  markRead: (id: string) => api.post<void>(`/notifications/${id}/read`),
  markAllRead: () => api.post<void>('/notifications/read-all'),
};
