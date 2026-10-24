/* @ds-bundle: {"format":4,"namespace":"CoPlanly","components":[{"name":"Button"},{"name":"AddItemButton"},{"name":"PillChip"},{"name":"GroupLabel"},{"name":"SectionGroup"},{"name":"SectionRow"},{"name":"InlineBanner"},{"name":"EmptyState"},{"name":"ErrorState"},{"name":"StickyActionBar"},{"name":"ConfirmationDialog"},{"name":"ConnectivityBanner"},{"name":"InviteCodeText"},{"name":"MiniMonth"},{"name":"ChangeRequestCard"},{"name":"DaySwapCard"},{"name":"ChatBubble"},{"name":"ChatDaySeparator"}]} */
(function () {
  var React = window.React, h = React.createElement;
  function cx() { return Array.prototype.filter.call(arguments, Boolean).join(' '); }
  function Icon(p) { return h('span', { className: cx('cp-icon', p.className), style: { fontSize: p.size || 24 }, 'aria-hidden': 'true' }, p.name); }

  function Button(p) {
    var v = p.variant || 'filled';
    return h('button', { type: 'button', className: cx('cp-btn', 'cp-btn-' + v, p.className), disabled: p.disabled, onClick: p.onClick },
      p.icon ? h(Icon, { name: p.icon, size: 18 }) : null, h('span', null, p.children));
  }
  function AddItemButton(p) { return h(Button, { variant: 'tonal', icon: 'add', onClick: p.onClick }, p.label); }

  function PillChip(p) {
    var style = {};
    if (p.container) style.background = p.container;
    if (p.contentColor) style.color = p.contentColor;
    var Tag = p.onClick ? 'button' : 'span';
    return h(Tag, { type: p.onClick ? 'button' : undefined, className: cx('cp-pill', !p.container && 'cp-pill-outlined', p.onClick && 'cp-pill-action'), style: style, onClick: p.onClick, 'aria-pressed': p.selected },
      p.leadingDot ? h('span', { className: 'cp-dot', style: { background: p.leadingDot } }) : null,
      p.icon ? h(Icon, { name: p.icon, size: 16 }) : null,
      h('span', { className: 'cp-pill-label label-medium' }, p.label));
  }

  function GroupLabel(p) { return h('h3', { className: 'cp-group-label label-medium-emphasized' }, p.text); }

  function SectionGroup(p) {
    var kids = React.Children.toArray(p.children), out = [];
    kids.forEach(function (k, i) { if (i) out.push(h('hr', { key: 'd' + i, className: 'cp-divider' })); out.push(k); });
    return h('div', { className: 'cp-section-group' }, out);
  }
  function SectionRow(p) {
    var Tag = p.onClick ? 'button' : 'div';
    return h(Tag, { type: p.onClick ? 'button' : undefined, className: cx('cp-row', p.onClick && 'cp-row-action', p.destructive && 'cp-row-destructive'), onClick: p.onClick },
      p.icon ? h(Icon, { name: p.icon, className: 'cp-row-icon' }) : null,
      h('span', { className: 'cp-row-text' },
        h('span', { className: 'cp-row-title body-large' }, p.title),
        p.supporting ? h('span', { className: 'cp-row-supporting body-small' },
          p.supportingDot ? h('span', { className: 'cp-dot', style: { background: p.supportingDot } }) : null, p.supporting) : null),
      p.trailing || null);
  }

  function InlineBanner(p) {
    var tone = p.tone || 'info';
    return h('div', { className: 'cp-banner cp-banner-' + tone, role: 'status' },
      p.icon ? h(Icon, { name: p.icon, size: 18, className: 'cp-banner-icon' }) : null,
      h('div', { className: 'cp-banner-body' },
        p.title ? h('div', { className: 'cp-banner-title title-small' }, p.title) : null,
        h('div', { className: 'cp-banner-text body-medium' }, p.text),
        p.actions ? h('div', { className: 'cp-banner-actions' }, p.actions) : null));
  }

  function HeroIcon(p) { return h('span', { className: 'cp-hero' }, h(Icon, { name: p.icon, size: 36 })); }
  function EmptyState(p) {
    return h('div', { className: 'cp-empty' }, h(HeroIcon, { icon: p.icon }),
      h('div', { className: 'title-medium cp-empty-title' }, p.title),
      p.description ? h('div', { className: 'body-medium cp-empty-desc' }, p.description) : null,
      p.actionLabel && p.onAction ? h(Button, { onClick: p.onAction }, p.actionLabel) : null);
  }
  function ErrorState(p) { return h(EmptyState, { icon: 'error', title: p.message, actionLabel: p.onRetry ? (p.retryLabel || 'Retry') : null, onAction: p.onRetry }); }

  function StickyActionBar(p) {
    return h('div', { className: 'cp-sticky' }, p.notice || null,
      h('button', { type: 'button', className: 'cp-btn cp-btn-filled cp-sticky-btn', disabled: p.enabled === false || p.busy, onClick: p.onClick },
        p.busy ? h('span', { className: 'cp-spinner', 'aria-label': 'Saving' }) : p.label));
  }

  function ConfirmationDialog(p) {
    return h('div', { className: 'cp-dialog', role: 'alertdialog', 'aria-label': p.title },
      h('div', { className: 'headline-small cp-dialog-title' }, p.title),
      h('div', { className: 'body-large cp-dialog-text' }, p.message),
      h('div', { className: 'cp-dialog-actions' },
        h(Button, { variant: 'text', onClick: p.onDismiss }, p.dismissText || 'Cancel'),
        h(Button, { variant: p.destructive ? 'text-error' : 'text', onClick: p.onConfirm }, p.confirmText || 'Confirm')));
  }

  function ConnectivityBanner(p) {
    if (!p.offline) return null;
    return h('div', { className: 'cp-offline label-medium', role: 'status', 'aria-live': 'polite' }, h(Icon, { name: 'cloud_off', size: 18 }), p.text || "You’re offline. Changes are kept and will sync when you’re back online.");
  }

  function InviteCodeText(p) { return h('span', { className: 'display-small cp-code', 'aria-label': p.code.split('').join(' ') }, p.code); }

  // A month from Monday: weekend base, custody wash overlay, event dots in a ring, today's disc.
  function MiniMonth(p) {
    var days = p.days || 30, offset = p.startOffset || 0, cells = [], who = p.custody || function () { return null; };
    var heads = p.weekdays || ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    for (var i = 0; i < offset; i++) cells.push(h('span', { key: 'e' + i, className: 'cp-mm-cell cp-mm-empty' }));
    for (var d = 1; d <= days; d++) {
      var col = (offset + d - 1) % 7, parent = who(d), dots = (p.events && p.events[d]) || [];
      cells.push(h('span', { key: d, className: cx('cp-mm-cell', col >= 5 && 'cp-mm-weekend', p.vacation && p.vacation.indexOf(d) >= 0 && 'cp-mm-vacation') },
        h('span', { className: 'cp-mm-wash', style: parent ? { background: 'var(--parent-' + parent + '-wash)' } : null }),
        h('span', { className: cx('cp-mm-num', 'body-small', d === p.today && 'cp-mm-today', p.holidays && p.holidays.indexOf(d) >= 0 && 'cp-mm-holiday') }, d),
        dots.length ? h('span', { className: 'cp-mm-dots' }, dots.map(function (c, j) { return h('span', { key: j, className: 'cp-mm-dot', style: { background: 'var(--parent-' + c + ')' } }); })) : null));
    }
    return h('div', { className: 'cp-mm' }, h('div', { className: 'cp-mm-grid' },
      heads.map(function (w, i) { return h('span', { key: 'h' + i, className: 'cp-mm-head label-small' }, w); }), cells));
  }


  // Requests inbox (changerequests/ChangeRequestsScreen.kt)
  function StatusPill(p) {
    return h(PillChip, { label: p.queued ? (p.queuedLabel || 'Queued') : p.status, icon: p.queued ? 'schedule' : null, contentColor: 'var(--on-surface-variant)' });
  }
  function ChangeRequestCard(p) {
    var pending = !p.status || p.status === 'Pending';
    return h('div', { className: cx('cp-card', 'cp-card-request', p.highlighted && 'cp-card-highlight') },
      p.banner || null,
      h('div', { className: 'cp-card-head' }, h('span', { className: 'title-medium cp-card-title' }, p.title),
        h(StatusPill, { status: p.status || 'Pending', queued: p.queued })),
      h('div', { className: 'cp-cr-times' },
        h('span', { className: 'cp-cr-time' }, h('span', { className: 'label-small cp-muted' }, p.currentLabel || 'Current'), h('span', { className: 'body-medium' }, p.current)),
        h(Icon, { name: 'arrow_forward', className: 'cp-cr-arrow' }),
        h('span', { className: 'cp-cr-time cp-cr-proposed' }, h('span', { className: 'label-small cp-muted' }, p.proposedLabel || 'Proposed'), h('span', { className: 'body-medium-emphasized' }, p.proposed))),
      p.note ? h('div', { className: 'body-small cp-muted' }, '“' + p.note + '”') : null,
      p.deadline ? h('div', { className: 'label-medium cp-muted cp-card-deadline' }, h(Icon, { name: 'schedule', size: 16 }), p.deadline) : null,
      pending && p.incoming ? h('div', { className: 'cp-card-actions cp-card-actions-split' },
        h(Button, { onClick: p.onAccept }, p.acceptLabel || 'Accept'), h(Button, { variant: 'outlined', onClick: p.onDecline }, p.declineLabel || 'Decline')) : null,
      pending && !p.incoming ? h('div', { className: 'cp-card-actions cp-card-actions-split' },
        h(Button, { variant: 'outlined', onClick: p.onWithdraw }, p.withdrawLabel || 'Withdraw request')) : null);
  }
  function DaySwapCard(p) {
    return h('div', { className: 'cp-card cp-card-swap' },
      p.banner || null,
      h('div', { className: 'cp-card-head' }, h('span', { className: 'title-medium cp-card-title' }, p.title), p.noAnswer ? h(PillChip, { label: 'No answer', contentColor: 'var(--on-surface-variant)' }) : null),
      h('div', { className: 'body-medium cp-muted' }, p.sentence),
      p.note ? h('div', { className: 'body-medium' }, p.note) : null,
      h('div', { className: 'label-medium cp-muted' }, p.status || 'Pending'),
      p.month || null,
      p.incoming ? h('div', { className: 'cp-card-actions' }, h(Button, { onClick: p.onAccept }, 'Accept'), h(Button, { variant: 'text', onClick: p.onDecline }, 'Decline'))
        : h('div', { className: 'cp-card-actions' }, h(Button, { variant: 'text', onClick: p.onWithdraw }, 'Withdraw request')));
  }

  // Chat (chat/MessagesList.kt)
  var RECEIPT = { sending: ['schedule', 'Sending'], sent: ['check', 'Sent'], delivered: ['done_all', 'Delivered'], read: ['done_all', 'Read'] };
  function ChatBubble(p) {
    var mine = !!p.mine, first = p.startsGroup !== false, last = p.endsGroup !== false, r = RECEIPT[p.status || 'read'];
    var inner = { top: first ? 'var(--radius-bubble)' : '6px', bottom: last ? '4px' : '6px' }, R = 'var(--radius-bubble)';
    var radius = mine ? [R, inner.top, inner.bottom, R] : [inner.top, R, R, inner.bottom];
    var Tag = p.onOpen ? 'button' : 'div';
    return h('div', { className: cx('cp-msg', mine && 'cp-msg-mine', first && 'cp-msg-first') },
      h(Tag, { type: p.onOpen ? 'button' : undefined, onClick: p.onOpen, className: cx('cp-bubble', mine ? 'cp-bubble-mine' : 'cp-bubble-theirs', p.onOpen && 'cp-bubble-card'), style: { borderRadius: radius.join(' ') } },
        h('span', { className: 'body-medium cp-bubble-text' }, p.text),
        p.onOpen ? h(Icon, { name: 'chevron_right', size: 18, className: 'cp-bubble-chevron' }) : null,
        h('span', { className: 'cp-bubble-meta label-small' }, h('span', { className: 'cp-meta-time' }, p.time),
          mine && p.status !== 'error' ? h('span', { className: cx('cp-receipt', p.status === 'read' && 'cp-receipt-read'), role: 'img', 'aria-label': r[1] }, h(Icon, { name: r[0], size: 13 })) : null)),
      mine && p.status === 'error' ? h('button', { type: 'button', className: 'cp-msg-error label-small', onClick: p.onRetry }, h(Icon, { name: 'error', size: 13 }), 'Not sent — tap to retry') : null);
  }
  function ChatDaySeparator(p) { return h('div', { className: 'cp-day-sep' }, h('span', { className: 'label-small' }, p.label)); }

  window.CoPlanly = { Button: Button, AddItemButton: AddItemButton, PillChip: PillChip, GroupLabel: GroupLabel, SectionGroup: SectionGroup, SectionRow: SectionRow, InlineBanner: InlineBanner, EmptyState: EmptyState, ErrorState: ErrorState, StickyActionBar: StickyActionBar, ConfirmationDialog: ConfirmationDialog, ConnectivityBanner: ConnectivityBanner, InviteCodeText: InviteCodeText, MiniMonth: MiniMonth, ChangeRequestCard: ChangeRequestCard, DaySwapCard: DaySwapCard, ChatBubble: ChatBubble, ChatDaySeparator: ChatDaySeparator, Icon: Icon };
})();
