document.addEventListener('DOMContentLoaded', () => {
  // If already logged in, redirect away from auth pages
  const token = getToken();
  if (token) {
    const claims = decodeToken(token);
    if (claims && Date.now() / 1000 < claims.exp) {
      window.location.href = claims.role === 'ADMIN' ? '/admin.html' : '/dashboard.html';
      return;
    }
  }

  const loginForm = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');
  const errorEl = document.getElementById('error-msg');

  function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.add('visible');
  }

  function hideError() {
    errorEl.classList.remove('visible');
  }

  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideError();
      const btn = loginForm.querySelector('button[type=submit]');
      btn.disabled = true;
      btn.textContent = 'Signing in…';

      try {
        const res = await fetch('/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: loginForm.email.value.trim(),
            password: loginForm.password.value,
          }),
        });

        const data = await res.json();

        if (!res.ok) {
          showError(data.detail || 'Login failed. Check your credentials.');
          return;
        }

        setToken(data.token);
        const claims = decodeToken(data.token);
        window.location.href = claims?.role === 'ADMIN' ? '/admin.html' : '/dashboard.html';
      } catch {
        showError('Network error. Is the API running?');
      } finally {
        btn.disabled = false;
        btn.textContent = 'Sign in';
      }
    });
  }

  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideError();

      const password = registerForm.password.value;
      const confirm = registerForm.confirm.value;
      if (password !== confirm) {
        showError('Passwords do not match.');
        return;
      }

      const btn = registerForm.querySelector('button[type=submit]');
      btn.disabled = true;
      btn.textContent = 'Creating account…';

      try {
        const res = await fetch('/api/v1/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: registerForm.fullname.value.trim(),
            email: registerForm.email.value.trim(),
            password,
          }),
        });

        const data = await res.json();

        if (!res.ok) {
          showError(data.detail || 'Registration failed.');
          return;
        }

        setToken(data.token);
        window.location.href = '/dashboard.html';
      } catch {
        showError('Network error. Is the API running?');
      } finally {
        btn.disabled = false;
        btn.textContent = 'Create account';
      }
    });
  }
});
