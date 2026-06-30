document.addEventListener('DOMContentLoaded', async () => {
  const claims = requireAuth('USER');
  if (!claims) return;

  // Set user info in sidebar
  document.getElementById('user-email').textContent = claims.email;
  document.getElementById('user-avatar').textContent = claims.email[0].toUpperCase();
  document.getElementById('logout-btn').addEventListener('click', logout);

  const pages = {
    overview: loadOverview,
    subscription: loadSubscription,
    invoices: loadInvoices,
    usage: loadUsage,
    payments: loadPayments,
  };

  // Nav
  document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', () => {
      const target = item.dataset.page;
      if (!target) return;
      document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
      document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
      item.classList.add('active');
      document.getElementById(target).classList.add('active');
      pages[target]?.();
    });
  });

  // Load whichever page is active by default (overview)
  const activeNav = document.querySelector('.nav-item.active');
  pages[activeNav?.dataset.page ?? 'overview']?.();
});

function logout() {
  clearToken();
  window.location.href = '/login.html';
}

async function loadOverview() {
  try {
    const [subRes, invoiceRes, usageRes] = await Promise.all([
      apiGet('/subscriptions/current'),
      apiGet('/invoices?page=0&size=1'),
      apiGet('/usage/summary'),
    ]);

    if (subRes.ok) {
      const sub = await subRes.json();
      document.getElementById('stat-period').textContent =
        sub.currentPeriodEnd ? formatDate(sub.currentPeriodEnd) : '—';
      document.getElementById('stat-status').innerHTML = statusBadge(sub.status);
    } else {
      document.getElementById('stat-period').textContent = '—';
      document.getElementById('stat-status').innerHTML = statusBadge('CANCELLED');
    }

    if (invoiceRes.ok) {
      const page = await invoiceRes.json();
      document.getElementById('stat-invoices').textContent = page.totalElements ?? 0;
    }

    if (usageRes.ok) {
      const usage = await usageRes.json();
      renderOverviewUsage(usage.metrics || []);
    } else {
      document.getElementById('overview-usage').innerHTML =
        '<p class="empty-state">No active subscription — pick a plan to start tracking usage.</p>';
    }
  } catch (err) {
    console.error(err);
  }
}

function renderOverviewUsage(metrics) {
  const el = document.getElementById('overview-usage');
  if (!metrics.length) { el.innerHTML = '<p class="empty-state">No usage data.</p>'; return; }
  el.innerHTML = metrics.map(m => {
    const pct = m.included > 0 ? Math.min((m.consumed / m.included) * 100, 100) : 0;
    const over = m.consumed > m.included;
    return `<div class="metric-row">
      <div class="metric-header">
        <span class="metric-name">${m.metric.replace('_', ' ')}</span>
        <span class="metric-counts">${m.consumed.toLocaleString()} / ${m.included.toLocaleString()}</span>
      </div>
      <div class="progress"><div class="progress-bar${over ? ' over' : ''}" style="width:${pct}%"></div></div>
    </div>`;
  }).join('');
}

async function loadSubscription() {
  const el = document.getElementById('sub-content');
  el.innerHTML = '<div class="spinner"></div>';

  const res = await apiGet('/subscriptions/current');
  if (!res.ok) {
    el.innerHTML = `<div class="empty-state"><p>No active subscription.</p><br>
      <p style="color:var(--muted);font-size:13px;">Pick a plan below to get started.</p>
    </div>`;
    await loadPlanOptions('subscribe');
    return;
  }

  const sub = await res.json();
  const planRes = await apiGet('/plans/' + sub.planId);
  const plan = planRes.ok ? await planRes.json() : null;

  let pendingNotice = '';
  if (sub.pendingPlanId) {
    const pendingPlanRes = await apiGet('/plans/' + sub.pendingPlanId);
    const pendingPlan = pendingPlanRes.ok ? await pendingPlanRes.json() : null;
    pendingNotice = `<div class="card" style="border-color:var(--warning,#caa53d);margin-top:12px;">
      Switching to <strong>${pendingPlan?.name ?? 'a new plan'}</strong> at the start of your next
      billing period on ${formatDate(sub.currentPeriodEnd)}.
    </div>`;
  }

  el.innerHTML = `
    <div class="card">
      <div class="card-title">Current Plan</div>
      <div class="plan-info">
        <div>
          <div class="plan-name">${plan?.name ?? 'Unknown Plan'}</div>
          <div style="color:var(--muted);margin-top:4px;">Billing ${plan?.billingInterval ?? ''}</div>
        </div>
        <div class="plan-price">${plan ? formatCents(plan.basePriceCents) : '—'}<span>/mo</span></div>
      </div>
      <div style="margin-top:16px; display:flex; gap:12px; flex-wrap:wrap;">
        <div><span style="color:var(--muted);font-size:12px;">PERIOD</span><br>
          ${formatDate(sub.currentPeriodStart)} – ${formatDate(sub.currentPeriodEnd)}</div>
        <div><span style="color:var(--muted);font-size:12px;">STATUS</span><br>${statusBadge(sub.status)}</div>
        ${sub.cancelledAt ? `<div><span style="color:var(--muted);font-size:12px;">CANCELLED</span><br>${formatDate(sub.cancelledAt)}</div>` : ''}
      </div>
      <div class="sub-actions">
        <button class="btn btn-ghost btn-sm" onclick="loadChangePlan()">Change plan</button>
        <button class="btn btn-danger btn-sm" onclick="cancelSub()">Cancel</button>
      </div>
    </div>
    ${pendingNotice}`;
}

async function cancelSub() {
  if (!confirm('Cancel your subscription?')) return;
  const res = await apiDelete('/subscriptions/current');
  if (res.ok) {
    showToast('Subscription cancelled.');
    loadSubscription();
  } else {
    showToast('Failed to cancel subscription.', 'error');
  }
}

async function loadPlanOptions(mode = 'subscribe') {
  const res = await apiGet('/plans');
  if (!res.ok) return;
  const plans = await res.json();
  const el = document.getElementById('plan-select-area');
  if (!el) return;
  const handler = mode === 'change' ? 'changePlanTo' : 'subscribeToPlan';
  el.style.display = 'block';
  el.innerHTML = `<div class="card"><div class="card-title">${mode === 'change' ? 'Choose a New Plan' : 'Available Plans'}</div>
    <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:14px;">
      ${plans.map(p => `
        <div class="card" style="margin:0;cursor:pointer;" onclick="${handler}('${p.planId}')">
          <div style="font-weight:700;font-size:16px;">${p.name}</div>
          <div style="font-size:22px;font-weight:700;margin:8px 0;">${formatCents(p.basePriceCents)}<span style="font-size:13px;color:var(--muted)">/mo</span></div>
          ${p.metricLimits.map(m => `<div style="font-size:12px;color:var(--muted);">${m.includedQuantity.toLocaleString()} ${m.metric.replace('_',' ')} included</div>`).join('')}
        </div>`).join('')}
    </div></div>`;
}

async function subscribeToPlan(planId) {
  const res = await apiPost('/subscriptions', { planId });
  if (res.ok) {
    showToast('Subscribed!');
    loadSubscription();
  } else {
    const err = await res.json();
    showToast(err.detail || 'Failed to subscribe.', 'error');
  }
}

async function changePlanTo(planId) {
  const res = await apiPost('/subscriptions/current/change-plan', { newPlanId: planId });
  if (res.ok) {
    const data = await res.json();
    showToast(data.message || 'Plan updated.');
    document.getElementById('plan-select-area').style.display = 'none';
    loadSubscription();
  } else {
    const err = await res.json();
    showToast(err.message || err.detail || 'Failed to change plan.', 'error');
  }
}

async function loadChangePlan() {
  await loadPlanOptions('change');
}

async function loadInvoices() {
  const el = document.getElementById('invoices-list');
  el.innerHTML = '<div class="spinner"></div>';

  const res = await apiGet('/invoices?page=0&size=20');
  if (!res.ok) { el.innerHTML = '<p>Failed to load invoices.</p>'; return; }
  const page = await res.json();

  if (!page.content?.length) {
    el.innerHTML = '<div class="empty-state"><p>No invoices yet.</p></div>';
    return;
  }

  el.innerHTML = `<div class="table-wrap"><table>
    <thead><tr><th>Period</th><th>Total</th><th>Status</th><th>Due</th></tr></thead>
    <tbody>${page.content.map(inv => `<tr>
      <td>${formatDate(inv.periodStart)} – ${formatDate(inv.periodEnd)}</td>
      <td>${formatCents(inv.totalCents)}</td>
      <td>${statusBadge(inv.status)}</td>
      <td>${formatDate(inv.dueDate)}</td>
    </tr>`).join('')}</tbody>
  </table></div>`;
}

async function loadUsage() {
  const el = document.getElementById('usage-content');
  el.innerHTML = '<div class="spinner"></div>';

  const res = await apiGet('/usage/summary');
  if (!res.ok) { el.innerHTML = '<p>Failed to load usage.</p>'; return; }
  const data = await res.json();

  if (!data.metrics?.length) {
    el.innerHTML = '<div class="empty-state"><p>No usage data.</p></div>';
    return;
  }

  el.innerHTML = data.metrics.map(m => {
    const pct = m.included > 0 ? Math.min((m.consumed / m.included) * 100, 100) : 0;
    const over = m.consumed > m.included;
    return `<div class="metric-row">
      <div class="metric-header">
        <span class="metric-name" style="font-size:15px;font-weight:600;">${m.metric.replace('_', ' ')}</span>
        <span class="metric-counts">${m.consumed.toLocaleString()} / ${m.included.toLocaleString()} &nbsp;|&nbsp; overage: ${m.overage.toLocaleString()}</span>
      </div>
      <div class="progress"><div class="progress-bar${over ? ' over' : ''}" style="width:${pct}%"></div></div>
    </div>`;
  }).join('');
}

async function loadPayments() {
  loadDemoDate();
  loadWallet();

  const el = document.getElementById('payments-list');
  el.innerHTML = '<div class="spinner"></div>';

  const res = await apiGet('/invoices?page=0&size=50');
  if (!res.ok) { el.innerHTML = '<p>Failed to load payments.</p>'; return; }
  const page = await res.json();
  const paid = page.content?.filter(i => i.status === 'PAID') || [];

  if (!paid.length) {
    el.innerHTML = '<div class="empty-state"><p>No payments yet.</p></div>';
    return;
  }

  el.innerHTML = `<div class="table-wrap"><table>
    <thead><tr><th>Period</th><th>Amount</th><th>Status</th></tr></thead>
    <tbody>${paid.map(inv => `<tr>
      <td>${formatDate(inv.periodStart)} – ${formatDate(inv.periodEnd)}</td>
      <td>${formatCents(inv.totalCents)}</td>
      <td>${statusBadge('PAID')}</td>
    </tr>`).join('')}</tbody>
  </table></div>`;
}

async function loadDemoDate() {
  const el = document.getElementById('demo-date');
  if (!el) return;
  const res = await apiGet('/time');
  if (res.ok) {
    const data = await res.json();
    el.textContent = formatDate(data.today);
  }
}

async function loadWallet() {
  const el = document.getElementById('wallet-balance');
  if (!el) return;
  const res = await apiGet('/payments/wallet');
  if (res.ok) {
    const data = await res.json();
    el.textContent = formatCents(data.balanceCents);
    el.style.color = data.balanceCents <= 0 ? 'var(--danger, #d9534f)' : '';
  }
}

async function advanceTime() {
  const btn = document.getElementById('advance-time-btn');
  btn.disabled = true;
  try {
    const res = await apiPost('/time/advance?days=15', {});
    if (res.ok) {
      const data = await res.json();
      showToast(data.message || 'Time advanced.');
      document.getElementById('demo-date').textContent = formatDate(data.today);
      await loadPayments(); // refreshes the payment list, demo date, and wallet balance
    } else {
      showToast('Failed to advance time.', 'error');
    }
  } finally {
    btn.disabled = false;
  }
}
