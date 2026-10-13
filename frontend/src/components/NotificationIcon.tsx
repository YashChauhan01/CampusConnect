import { HeartHandshake, MessageCircle, Send, Sparkles, Trophy, UserCheck, XCircle } from 'lucide-react';
import type { NotificationType } from '../api/types';

const ICONS: Record<NotificationType, typeof Sparkles> = {
  MATCH_PROPOSED: Sparkles,
  MATCH_REQUESTED: Send,
  MATCH_PARTNER_ACCEPTED: UserCheck,
  MATCH_CONFIRMED: HeartHandshake,
  MATCH_DECLINED: XCircle,
  MATCH_EXPIRED: XCircle,
  MATCH_CANCELLED: XCircle,
  CHAT_MESSAGE: MessageCircle,
  TEAMS_PUBLISHED: Trophy,
};

export function NotificationIcon({ type }: { type: NotificationType }) {
  const Icon = ICONS[type] ?? Sparkles;
  return (
    <span className={`notif-icon notif-${type.toLowerCase()}`} aria-hidden="true">
      <Icon />
    </span>
  );
}
