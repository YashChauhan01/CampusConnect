import type { CheckInRequest, PresenceView, Profile } from './api/types';

/**
 * Check-in request for renewing or moving the current check-in: keeps the status, the subjects the student is working
 * on and the note, so pressing "Renew" or picking another zone never silently wipes them. Without a current check-in
 * it is a plain "available" check-in.
 */
export function checkInRequest(zoneId: number, current: PresenceView | undefined, profile: Profile | null): CheckInRequest {
  if (!current) return { zoneId, status: 'AVAILABLE' };
  const byName = new Map((profile?.subjects ?? []).map((s) => [s.name, s.id]));
  return {
    zoneId,
    status: current.status,
    requirements: current.requirements ?? undefined,
    seekingSubjectIds: current.seeking.map((n) => byName.get(n)).filter((id): id is number => id !== undefined),
  };
}
