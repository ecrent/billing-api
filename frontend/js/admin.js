document.addEventListener('DOMContentLoaded', async () => {
  const claims = requireAuth('ADMIN');
  if (!claims) return;

  document.getElementById('user-email').textContent = claims.email;
  document.getElementById('user-avatar').textContent = 'A';
  document.getElementById('logout-btn').addEventListener('click', () => {
    clearToken();
    window.location.href = '/login.html';
  });

  const pages = { tenants: loadTenants, plans: loadPlans };

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

  await loadTenants();
});

async function loadTenants() {
  const el = document.getElementById('tenants-list');
  el.innerHTML = '<div class="spinner"></div>';

  // Load a few pages of tenants
  const res = await apiGet('/tenants?page=0&size=50');
  if (!res) return;

  if (!res.ok) {
    el.innerHTML = '<p style="color:var(--muted)">No tenant listing endpoint available (GET /tenants not implemented). Use GET /tenants/{id}.</p>';
    return;
  }

  const page = await res.json();
  const tenants = page.content || [];

  if (!tenants.length) {
    el.innerHTML = '<div class="empty-state"><p>No tenants yet. Register to create the first one.</p></div>';
    return;
  }

  el.innerHTML = `<div class="table-wrap"><table>
    <thead><tr><th>Name</th><th>Email</th><th>Status</th><th>Created</th></tr></thead>
    <tbody>${tenants.map(t => `<tr>
      <td>${t.name}</td>
      <td>${t.email}</td>
      <td>${statusBadge(t.status)}</td>
      <td>${formatDate(t.createdAt)}</td>
    </tr>`).join('')}</tbody>
  </table></div>`;
}

async function loadPlans() {
  const el = document.getElementById('plans-list');
  el.innerHTML = '<div class="spinner"></div>';

  const res = await apiGet('/plans');
  if (!res.ok) { el.innerHTML = '<p>Failed to load plans.</p>'; return; }
  const plans = await res.json();

  if (!plans.length) {
    el.innerHTML = '<div class="empty-state"><p>No plans configured.</p></div>';
    return;
  }

  el.innerHTML = `<div class="table-wrap"><table>
    <thead><tr><th>Name</th><th>Slug</th><th>Base Price</th><th>Interval</th><th>Status</th></tr></thead>
    <tbody>${plans.map(p => `<tr>
      <td style="font-weight:600">${p.name}</td>
      <td><code style="background:var(--surface2);padding:2px 8px;border-radius:4px;font-size:12px;">${p.slug}</code></td>
      <td>${formatCents(p.basePriceCents)}</td>
      <td>${p.billingInterval}</td>
      <td>${statusBadge(p.status)}</td>
    </tr>`).join('')}</tbody>
  </table></div>`;
}
