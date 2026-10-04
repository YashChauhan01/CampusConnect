export type Proficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

export const PROFICIENCIES: Proficiency[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

export interface Item {
  id: number;
  name: string;
  proficiency: Proficiency;
}

export interface Profile {
  id: string;
  fullName: string;
  email: string;
  bio: string | null;
  skills: Item[];
  subjects: Item[];
}

export interface Zone {
  id: number;
  name: string;
}

export type PresenceStatus = 'AVAILABLE' | 'BUSY';

export interface SubjectLevel {
  name: string;
  proficiency: Proficiency;
}

export interface PresenceView {
  studentId: string;
  name: string;
  zoneId: number;
  zone: string;
  status: PresenceStatus;
  requirements: string | null;
  seeking: string[];
  subjects: SubjectLevel[];
  expiresAt: string;
}

export interface CheckInRequest {
  zoneId: number;
  status: PresenceStatus;
  requirements?: string;
  seekingSubjectIds?: number[];
}

export interface Breakdown {
  knowledge: number;
  reciprocity: number;
  breadth: number;
  proximity: number;
}

export type MatchStatus = 'PROPOSED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'COMPLETED';

export interface Partner {
  id: string;
  name: string;
  zone: string | null;
  bio: string | null;
  email: string | null;
}

export interface MatchView {
  id: string;
  status: MatchStatus;
  score: number;
  breakdown: Breakdown;
  partner: Partner;
  sharedSubjects: string[];
  youAccepted: boolean;
  partnerAccepted: boolean;
  createdAt: string;
  expiresAt: string;
}

export interface Suggestion {
  studentId: string;
  name: string;
  zone: string;
  score: number;
  sharedSubjects: string[];
  breakdown: Breakdown;
}

export type HackathonStatus = 'OPEN' | 'CLOSED' | 'PUBLISHED';

export interface RoleView {
  id: number;
  name: string;
  keywords: string[];
}

export interface HackathonSummary {
  id: string;
  name: string;
  description: string | null;
  status: HackathonStatus;
  organizer: string;
  organizedByMe: boolean;
  participants: number;
  registered: boolean;
  roles: RoleView[];
  createdAt: string;
}

export interface ParticipantView {
  studentId: string;
  name: string;
  skills: string[];
  preferences: string[];
}

export interface HackathonDetail {
  summary: HackathonSummary;
  myPreferences: number[];
  participants: ParticipantView[];
}

export interface MemberView {
  studentId: string;
  name: string;
  role: string;
  fit: number;
  strength: number;
  you: boolean;
}

export interface TeamView {
  number: number;
  members: MemberView[];
  averageStrength: number;
  yours: boolean;
}

export interface TeamsView {
  status: HackathonStatus;
  published: boolean;
  teams: TeamView[];
  quality: {
    averageFit: number;
    preferenceSatisfaction: number;
    strengthStdDev: number;
    strengthSpread: number;
  };
}

export interface CreateHackathonRequest {
  name: string;
  description?: string;
  roles: { name: string; keywords: string[] }[];
}

export type NotificationType =
  | 'MATCH_PROPOSED'
  | 'MATCH_PARTNER_ACCEPTED'
  | 'MATCH_CONFIRMED'
  | 'MATCH_DECLINED'
  | 'MATCH_EXPIRED'
  | 'TEAMS_PUBLISHED';

export interface NotificationView {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  link: string | null;
  createdAt: string;
  read: boolean;
}

export interface Inbox {
  items: NotificationView[];
  unread: number;
}

/** Message delivered over the WebSocket. */
export interface Push {
  kind: 'NOTIFICATION' | 'REFRESH';
  topic: 'matches' | 'hackathons' | 'presence';
  notification: NotificationView | null;
}
