export type Action = {
  title: string;
  action: string;
  value: string;
  primary: boolean;
  destructive: boolean;
  enabled: boolean;
};
export type Field = {
  key: string;
  label: string;
  value: string;
  multiline: boolean;
  toggle: boolean;
  secret: boolean;
  choices: { value: string; label: string }[];
};
export type Card = {
  id: string;
  title: string;
  detail: string;
  badge: string;
  buttons: Action[];
  image: string;
  selection?: Action | null;
};
export type Section = { title: string; cards: Card[]; collapsed: boolean };
export type Wheel = {
  names: string[];
  round: {
    id: string;
    weights: number[];
    startAt: number;
    duration: number;
    turns: number;
    landingOffset: number;
    winnerId: string;
    memberIds: string[];
    seed: number;
  };
  serverOffset: number;
  winner: string;
  style: string;
};
export type QuickWheel = {
  people: { id: number; name: string; active: boolean; removable: boolean }[];
  history: { id: string; name: string; date: string }[];
  winner: string;
  spinning: boolean;
  canSpin: boolean;
  startRotation: number;
  endRotation: number;
  destination: string;
  nameDraft: string;
  error: string;
  revision: number;
};
export type Snapshot = {
  profile?: { name: string; photo: string } | null;
  notificationUnread?: number;
  adminAvailable?: boolean;
  supportActive: boolean;
  accountId: string;
  state: {
    page: string;
    title: string;
    subtitle: string;
    busy: boolean;
    online: boolean;
    status: string;
    error: string;
    wheel?: Wheel | null;
    roomCode: string;
    canGoBack: boolean;
    progressStep: number;
    inviteLink: string;
    inviteSummary: string;
    rtl: boolean;
    accessBlocked: boolean;
    canOverrideSelection: boolean;
    reminderEmailPrompt?: {
      name: string;
      address: string;
      error: string;
    } | null;
    walletHistoryPrompt?: {
      title: string;
      subtitle: string;
      balance: string;
      cards: Card[];
      loading: boolean;
      error: string;
      hasMore: boolean;
    } | null;
    feedback?: {
      id: number;
      message: string;
      isError: boolean;
      expiresAt: number;
    } | null;
    hasJoinTimer: boolean;
  };
  authenticated: boolean;
  mainFields: Field[];
  extraFields: Field[];
  topCards: Card[];
  sections: Section[];
  primaryAction?: Action | null;
  inlineButtons: Action[];
  utilityButtons: Action[];
  quick: QuickWheel;
};
