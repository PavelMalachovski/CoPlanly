/** CoPlanly web renditions — window.CoPlanly. Colours are CSS custom properties from tokens.json. */
type ParentColour = 'pink' | 'blue' | 'purple' | 'orange';
export interface ButtonProps { variant?: 'filled' | 'tonal' | 'outlined' | 'text' | 'text-error'; icon?: string; disabled?: boolean; onClick?: () => void; children: React.ReactNode }
export interface AddItemButtonProps { label: string; onClick: () => void }
export interface PillChipProps { label: string; icon?: string; container?: string; contentColor?: string; leadingDot?: string; onClick?: () => void; selected?: boolean }
export interface GroupLabelProps { text: string }
export interface SectionGroupProps { children: React.ReactNode }
export interface SectionRowProps { title: string; icon?: string; supporting?: string; supportingDot?: string; trailing?: React.ReactNode; onClick?: () => void; destructive?: boolean }
export interface InlineBannerProps { text: string; title?: string; icon?: string; tone?: 'info' | 'attention'; actions?: React.ReactNode }
export interface EmptyStateProps { icon: string; title: string; description?: string; actionLabel?: string; onAction?: () => void }
export interface ErrorStateProps { message: string; onRetry?: () => void; retryLabel?: string }
export interface StickyActionBarProps { label: string; onClick?: () => void; enabled?: boolean; busy?: boolean; notice?: React.ReactNode }
export interface ConfirmationDialogProps { title: string; message: string; confirmText?: string; dismissText?: string; onConfirm?: () => void; onDismiss?: () => void; destructive?: boolean }
export interface ConnectivityBannerProps { offline: boolean; text?: string }
export interface InviteCodeTextProps { code: string }
export interface MiniMonthProps { days?: number; startOffset?: number; weekdays?: string[]; custody?: (day: number) => ParentColour | null; events?: Record<number, ParentColour[]>; today?: number; holidays?: number[]; vacation?: number[] }
export interface ChangeRequestCardProps { title: string; current: string; proposed: string; status?: 'Pending' | 'Accepted' | 'Declined' | 'Withdrawn'; queued?: boolean; note?: string; deadline?: string; incoming?: boolean; highlighted?: boolean; banner?: React.ReactNode; onAccept?: () => void; onDecline?: () => void; onWithdraw?: () => void }
export interface DaySwapCardProps { title: string; sentence: string; note?: string; status?: string; incoming?: boolean; noAnswer?: boolean; month?: React.ReactNode; banner?: React.ReactNode; onAccept?: () => void; onDecline?: () => void; onWithdraw?: () => void }
export interface ChatBubbleProps { text: string; time: string; mine?: boolean; status?: 'sending' | 'sent' | 'delivered' | 'read' | 'error'; startsGroup?: boolean; endsGroup?: boolean; onOpen?: () => void; onRetry?: () => void }
export interface ChatDaySeparatorProps { label: string }
