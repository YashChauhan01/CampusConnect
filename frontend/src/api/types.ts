export type Proficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

export const PROFICIENCIES: Proficiency[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

export interface Item {
  id: number;
  name: string;
  proficiency: Proficiency;
  /** Level confirmed by an AI-assisted check; never above `proficiency`. */
  verifiedLevel: Proficiency | null;
}

export interface Profile {
  id: string;
  fullName: string;
  email: string;
  bio: string | null;
  admin: boolean;
  skills: Item[];
  subjects: Item[];
}

export interface Zone {
  id: number;
  name: string;
}

export interface AdminZone {
  id: number;
  name: string;
  enabled: boolean;
  x: number | null;
  y: number | null;
  checkedIn: number;
}

export interface ZoneInput {
  name: string;
  x: number | null;
  y: number | null;
  enabled?: boolean;
}

export type PresenceStatus = 'AVAILABLE' | 'BUSY';

export interface SubjectLevel {
  name: string;
  /** The level the platform trusts (claim capped by verification). */
  proficiency: Proficiency;
  verified: boolean;
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
  /** A request one student sent to another, rather than a pairing from a matching round. */
  direct: boolean;
  requestedByYou: boolean;
}

export interface ChatMessage {
  id: string;
  senderId: string;
  mine: boolean;
  body: string;
  createdAt: string;
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
  | 'TEAMS_PUBLISHED'
  | 'MATCH_REQUESTED'
  | 'MATCH_CANCELLED'
  | 'CHAT_MESSAGE';

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
  topic: 'matches' | 'hackathons' | 'presence' | 'chat';
  notification: NotificationView | null;
}

export interface AssessmentStatus {
  enabled: boolean;
  questionCount: number;
}

export interface Assessment {
  id: string;
  kind: 'SKILL' | 'SUBJECT';
  itemId: number;
  topic: string;
  claimedLevel: Proficiency;
  questions: string[];
  status: 'PENDING' | 'GRADED';
  score: number | null;
  verifiedLevel: Proficiency | null;
  feedback: string | null;
}

export interface GraphNode {
  index: number;
  id: string;
  name: string;
  zone: string;
  subjects: { name: string; level: number }[];
  partner: number | null;
  degree: number;
}

export interface GraphEdge {
  a: number;
  b: number;
  score: number;
  knowledge: number;
  reciprocity: number;
  breadth: number;
  proximity: number;
  shared: string[];
  matched: boolean;
}

export interface GraphSolution {
  pairs: number;
  totalWeight: number;
  millis: number;
  matchedStudents: number;
}

export interface GraphView {
  poolSize: number;
  edgesTotal: number;
  edgesShown: number;
  topK: number;
  nodes: GraphNode[];
  edges: GraphEdge[];
  optimal: GraphSolution;
  greedy: GraphSolution;
  graphBuildMillis: number;
  check: { pairsChecked: number; exchangesTested: number; improvingExchanges: number; passed: boolean };
  zones: string[];
}

export interface RoundSummary {
  startedAt: string;
  poolSize: number;
  edgeCount: number;
  pairs: number;
  totalWeight: number;
  durationMs: number;
}

export interface SynthesisMethod {
  averageFit: number;
  preferenceSatisfaction: number;
  strengthStdDev: number;
  strengthSpread: number;
  /** Share placed in their 1st, 2nd, 3rd choice and in an unranked role. */
  preferenceRanks: number[];
  /** Average strength (0-1) of every team, weakest first. */
  teamStrengths: number[];
  millis: number;
}

export interface AnalysisMember {
  studentId: string;
  name: string;
  roleId: number;
  fit: number;
  strength: number;
  preferenceRank: number | null;
  preferences: number[];
  skills: { name: string; level: number }[];
  /** Fit (percent) for every role, in role order. */
  fitByRole: number[];
}

export interface TeamAnalysis {
  hackathonId: string;
  name: string;
  status: HackathonStatus;
  participants: number;
  teamCount: number;
  roles: { id: number; name: string; keywords: string[]; holders: number; firstChoices: number }[];
  teams: { number: number; members: AnalysisMember[]; averageStrength: number }[];
  hungarian: SynthesisMethod;
  random: SynthesisMethod;
  randomRuns: number;
  check: {
    participants: number;
    roles: number;
    movesExamined: number;
    improvingCycles: number;
    totalFit: number;
    passed: boolean;
  };
  /** flow[i][j]: first choice role i (last row: no preference) allocated role j. */
  flow: number[][];
}
