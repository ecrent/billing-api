// Fetch wrapper — attaches Bearer token, handles 401 globally
const API_BASE = '/api/v1';

function getToken() { return localStorage.getItem('token'); }
function setToken(t) { localStorage.setItem('token', t); }
function clearToken() { localStorage.removeItem('token'); }

function decodeToken(token) {
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
  } catch { return null; }
}

async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;

  const res = await fetch(API_BASE + path, { ...options, headers });

  if (res.status === 401) {
    clearToken();
    window.location.href = '/login.html';
    return;
  }

  return res;
}

async function apiGet(path) {
  return apiFetch(path);
}

async function apiPost(path, body) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify(body) });
}

async function apiDelete(path) {
  return apiFetch(path, { method: 'DELETE' });
}

function formatCents(cents) {
  return '$' + (cents / 100).toFixed(2);
}

function formatDate(str) {
  if (!str) return '—';
  return new Date(str).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function statusBadge(status) {
  const map = {
    ACTIVE: 'success', PAID: 'success', SUCCEEDED: 'success',
    DRAFT: 'muted', PENDING: 'warning',
    CANCELLED: 'muted', VOID: 'muted',
    PAST_DUE: 'danger', FAILED: 'danger', FINALIZED: 'accent',
  };
  const cls = map[status] || 'muted';
  return `<span class="badge badge-${cls}">${status}</span>`;
}

function showToast(msg, type = 'success') {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = msg;
  el.className = `toast toast-${type} visible`;
  setTimeout(() => el.classList.remove('visible'), 3500);
}

function requireAuth(requiredRole) {
  const token = getToken();
  if (!token) { window.location.href = '/login.html'; return null; }
  const claims = decodeToken(token);
  if (!claims || Date.now() / 1000 > claims.exp) {
    clearToken();
    window.location.href = '/login.html';
    return null;
  }
  if (requiredRole && claims.role !== requiredRole) {
    window.location.href = claims.role === 'ADMIN' ? '/admin.html' : '/dashboard.html';
    return null;
  }
  return claims;
}
