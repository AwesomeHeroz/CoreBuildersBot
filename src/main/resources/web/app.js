'use strict';

const state = {
  me: null,
  csrf: null,
  page: 1,
  pageSize: 20,
  total: 0,
  items: [],
  categories: [],
  shop: null,
  myItems: [],
  cart: null
};

const fallbackImage = 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 400"><rect width="640" height="400" fill="#eceff1"/><rect x="230" y="125" width="180" height="150" rx="12" fill="#fff" stroke="#b0bec5" stroke-width="8"/><path d="m252 235 48-52 39 39 27-29 25 42z" fill="#7e57c2"/><circle cx="286" cy="164" r="17" fill="#b39ddb"/></svg>'
);

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));
let itemPreviewObjectUrl = null;

function initMaterialize() {
  const sidenavs = document.querySelectorAll('.sidenav');
  if (window.M?.Sidenav) {
    window.M.Sidenav.init(sidenavs);
  } else {
    const navigation = $('#mobile-navigation');
    $('.mobile-menu-button')?.addEventListener('click', () => navigation?.classList.add('sidenav-fallback-open'));
    navigation?.querySelectorAll('.sidenav-close').forEach((link) => {
      link.addEventListener('click', () => navigation.classList.remove('sidenav-fallback-open'));
    });
  }
  if (window.M?.Waves?.displayEffect) window.M.Waves.displayEffect();
}

function updateMaterialFields() {
  if (window.M?.updateTextFields) window.M.updateTextFields();
  document.querySelectorAll('textarea.materialize-textarea').forEach((textarea) => {
    if (window.M?.textareaAutoResize) window.M.textareaAutoResize(textarea);
  });
}

function openPopover(modal, focusTarget) {
  modal.setAttribute('aria-hidden', 'false');
  if (typeof modal.showPopover === 'function') modal.showPopover();
  else modal.classList.add('popover-fallback-open');
  document.body.classList.add('modal-open');
  window.setTimeout(() => (focusTarget || modal).focus?.(), 0);
}

function closePopover(modal) {
  modal.setAttribute('aria-hidden', 'true');
  if (typeof modal.hidePopover === 'function' && modal.matches(':popover-open')) modal.hidePopover();
  else modal.classList.remove('popover-fallback-open');
  if (!document.querySelector('.modal:popover-open, .modal.popover-fallback-open')) {
    document.body.classList.remove('modal-open');
  }
}

function showActionModal({
  title,
  message,
  confirmText = 'Continue',
  cancelText = 'Cancel',
  danger = false,
  input = null
}) {
  const modal = $('#action-modal');
  const previousFocus = document.activeElement;
  const titleNode = $('#action-modal-title');
  const messageNode = $('#action-modal-message');
  const confirmButton = $('#action-modal-confirm');
  const cancelButton = $('#action-modal-cancel');
  const closeButton = $('#action-modal-close');
  const errorNode = $('#action-modal-error');
  const inputGroup = $('#action-modal-input-group');
  const inputNode = $('#action-modal-input');

  titleNode.textContent = title || 'Please confirm';
  messageNode.textContent = message || '';
  confirmButton.textContent = confirmText;
  confirmButton.className = danger
    ? 'btn waves-effect waves-light red'
    : 'btn waves-effect waves-light deep-purple';
  cancelButton.textContent = cancelText;
  errorNode.classList.add('hidden');
  errorNode.textContent = '';

  if (input) {
    inputGroup.classList.remove('hidden');
    $('#action-modal-input-label').textContent = input.label || 'Details';
    $('#action-modal-input-help').textContent = input.help || '';
    inputNode.value = input.value || '';
    inputNode.minLength = input.minLength || 0;
    inputNode.maxLength = input.maxLength || 500;
    inputNode.placeholder = input.placeholder || '';
  } else {
    inputGroup.classList.add('hidden');
    inputNode.value = '';
    inputNode.removeAttribute('minlength');
    inputNode.removeAttribute('maxlength');
    inputNode.removeAttribute('placeholder');
  }
  updateMaterialFields();

  return new Promise((resolve) => {
    let settled = false;
    const controller = new AbortController();
    const { signal } = controller;

    const finish = (value) => {
      if (settled) return;
      settled = true;
      controller.abort();
      closePopover(modal);
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
      resolve(value);
    };

    const cancel = () => finish(input ? null : false);
    const accept = () => {
      if (!input) {
        finish(true);
        return;
      }
      const value = String(inputNode.value || '').trim();
      const minLength = input.minLength || 0;
      const maxLength = input.maxLength || 500;
      if (value.length < minLength || value.length > maxLength) {
        errorNode.textContent = `Enter between ${minLength} and ${maxLength} characters.`;
        errorNode.classList.remove('hidden');
        inputNode.focus();
        return;
      }
      finish(value);
    };

    confirmButton.addEventListener('click', accept, { signal });
    cancelButton.addEventListener('click', cancel, { signal });
    closeButton.addEventListener('click', cancel, { signal });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') cancel();
      if (event.key === 'Enter' && !input && event.target?.tagName !== 'TEXTAREA') accept();
    }, { signal });

    openPopover(modal, input ? inputNode : confirmButton);
  });
}

function showConfirmModal(options) {
  return showActionModal(options);
}

function showPromptModal(options) {
  return showActionModal(options);
}

function openRegisterModal() {
  const modal = $('#register-modal');
  const previousFocus = document.activeElement;
  const controller = new AbortController();
  const { signal } = controller;
  let closed = false;

  const close = () => {
    if (closed) return;
    closed = true;
    controller.abort();
    closePopover(modal);
    if (previousFocus instanceof HTMLElement) previousFocus.focus();
  };

  $('#register-close').addEventListener('click', close, { signal });
  $('#register-cancel').addEventListener('click', close, { signal });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  }, { signal });

  openPopover(modal, $('#register-discord-link'));
}

function openCodeLoginModal({
  verifyTitle,
  instructions,
  waitingText,
  confirmTitle,
  accountLabel,
  guidanceType
}) {
  const modal = $('#minecraft-login-modal');
  const previousFocus = document.activeElement;
  const controller = new AbortController();
  const { signal } = controller;
  let closed = false;
  let decisionResolver = null;

  const finishDecision = (accepted) => {
    if (!decisionResolver) return;
    const resolve = decisionResolver;
    decisionResolver = null;
    resolve(accepted);
  };

  const close = (accepted = false) => {
    if (closed) return;
    closed = true;
    finishDecision(accepted);
    controller.abort();
    closePopover(modal);
    if (previousFocus instanceof HTMLElement) previousFocus.focus();
  };

  $('#minecraft-login-title').textContent = verifyTitle;
  $('#minecraft-login-status').textContent = 'Creating a one-time login code…';
  $('#minecraft-login-code').textContent = '--------';
  $('#minecraft-login-command').textContent = '';
  $('#minecraft-login-instructions').textContent = instructions;
  $('#minecraft-login-account-label').textContent = accountLabel;
  $('#discord-login-guidance').classList.toggle('hidden', guidanceType !== 'discord');
  $('#minecraft-login-guidance').classList.toggle('hidden', guidanceType !== 'minecraft');
  $('#minecraft-login-challenge').classList.remove('hidden');
  $('#minecraft-login-account').classList.add('hidden');
  $('#minecraft-login-confirm').disabled = false;
  $('#minecraft-login-confirm').textContent = 'Continue';
  $('#minecraft-login-confirm').classList.add('hidden');
  $('#minecraft-login-cancel').textContent = 'Cancel';

  $('#minecraft-login-cancel').addEventListener('click', () => close(false), { signal });
  $('#minecraft-login-close').addEventListener('click', () => close(false), { signal });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close(false);
  }, { signal });

  openPopover(modal, $('#minecraft-login-cancel'));

  return {
    isOpen: () => !closed,
    showChallenge(challenge) {
      if (closed) return;
      $('#minecraft-login-code').textContent = challenge.code;
      $('#minecraft-login-command').textContent = challenge.command;
      $('#minecraft-login-status').textContent = waitingText;
    },
    confirmAccount(displayName) {
      if (closed) return Promise.resolve(false);
      $('#minecraft-login-title').textContent = confirmTitle;
      $('#minecraft-login-status').textContent = 'The login code was verified successfully.';
      $('#minecraft-login-challenge').classList.add('hidden');
      $('#minecraft-login-name').textContent = displayName;
      $('#minecraft-login-account').classList.remove('hidden');
      $('#minecraft-login-confirm').classList.remove('hidden');
      $('#minecraft-login-cancel').textContent = 'Use another account';
      return new Promise((resolve) => {
        decisionResolver = resolve;
        const confirmButton = $('#minecraft-login-confirm');
        const confirm = () => {
          confirmButton.removeEventListener('click', confirm);
          finishDecision(true);
          confirmButton.disabled = true;
          confirmButton.textContent = 'Signing in…';
        };
        confirmButton.addEventListener('click', confirm, { signal });
        confirmButton.focus();
      });
    },
    close
  };
}
const coins = (value) => `${new Intl.NumberFormat('en-US').format(value || 0)} coins`;
const shortCoins = (value) => `${new Intl.NumberFormat('en-US').format(value || 0)} coins`;
const dateTime = (value) => (value && value != 0) ? new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—';

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function image(url, className, alt) {
  const img = element('img', className);
  img.alt = alt || '';
  img.loading = 'lazy';
  img.src = url || fallbackImage;
  img.addEventListener('error', () => { img.src = fallbackImage; }, { once: true });
  return img;
}

async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
    options.body = JSON.stringify(options.body);
  }
  if (!['GET', 'HEAD'].includes(method) && state.csrf) headers.set('X-CSRF-Token', state.csrf);
  const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await response.json() : null;
  if (!response.ok) {
    const message = data?.error?.message || `Request failed with status ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.code = data?.error?.code;
    throw error;
  }
  return data;
}

function uploadListingImage(file) {
  const data = new FormData();
  data.append('image', file, file.name);
  $('#item-image-upload-status').textContent = 'Uploading image…';

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/me/shop/images');
    xhr.setRequestHeader('Accept', 'application/json');
    if (state.csrf) xhr.setRequestHeader('X-CSRF-Token', state.csrf);

    xhr.upload.addEventListener('progress', (event) => {
      if (!event.lengthComputable) return;
      const percent = Math.max(1, Math.round((event.loaded / event.total) * 100));
      $('#item-image-upload-status').textContent = `Uploading image… ${percent}%`;
    });

    xhr.addEventListener('load', () => {
      let payload = null;
      try { payload = JSON.parse(xhr.responseText || '{}'); }
      catch (_) { payload = null; }
      if (xhr.status >= 200 && xhr.status < 300) {
        $('#item-image-upload-status').textContent = 'Image uploaded.';
        resolve(payload);
        return;
      }
      const message = payload?.error?.message || `Image upload failed with status ${xhr.status}`;
      $('#item-image-upload-status').textContent = 'Image upload failed.';
      reject(new Error(message));
    });

    xhr.addEventListener('error', () => {
      $('#item-image-upload-status').textContent = 'Image upload failed.';
      reject(new Error('Image upload failed because of a network error.'));
    });

    xhr.addEventListener('abort', () => {
      $('#item-image-upload-status').textContent = 'Image upload cancelled.';
      reject(new Error('Image upload was cancelled.'));
    });

    xhr.send(data);
  });
}

function clearItemPreviewObjectUrl() {
  if (!itemPreviewObjectUrl) return;
  URL.revokeObjectURL(itemPreviewObjectUrl);
  itemPreviewObjectUrl = null;
}

function showItemImagePreview(url, status) {
  const preview = $('#item-image-preview');
  const previewImage = $('#item-image-preview-img');
  if (!url) {
    preview.classList.add('hidden');
    previewImage.removeAttribute('src');
    $('#item-image-upload-status').textContent = '';
    return;
  }
  previewImage.src = url;
  preview.classList.remove('hidden');
  $('#item-image-upload-status').textContent = status || 'Current listing image.';
}

function clearItemImage() {
  clearItemPreviewObjectUrl();
  $('#item-image-file').value = '';
  const filePath = document.querySelector('#item-form .file-path');
  if (filePath) filePath.value = '';
  $('#item-image').value = '';
  showItemImagePreview('', '');
}

function notify(message, isError = false) {
  const notice = $('#notice');
  notice.textContent = message;
  notice.classList.toggle('error', isError);
  notice.classList.remove('hidden');
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => notice.classList.add('hidden'), 6000);
}

async function loadMe() {
  const data = await api('/api/auth/me');
  state.me = data.authenticated ? data.user : null;
  state.csrf = data.csrfToken || null;
  renderAuth(data.balance || 0);
  renderAuthenticatedVisibility();
}

async function startCodeLogin({
  challengeEndpoint,
  completionEndpoint,
  displayField,
  modalOptions,
  successMessage
}) {
  let modal;
  try {
    modal = openCodeLoginModal(modalOptions);
    const challenge = await api(challengeEndpoint, { method: 'POST' });
    if (!modal.isOpen()) return;
    modal.showChallenge(challenge);

    while (modal.isOpen()) {
      await new Promise((resolve) => window.setTimeout(resolve, 1500));
      if (!modal.isOpen()) return;
      const result = await api(completionEndpoint, {
        method: 'POST',
        body: { challengeToken: challenge.challengeToken, confirm: false }
      });
      if (result.status === 'ready') {
        const accepted = await modal.confirmAccount(result[displayField] || 'Unknown');
        if (!accepted || !modal.isOpen()) {
          modal.close();
          notify('Login cancelled. Generate a new code when ready.');
          return;
        }
        const completed = await api(completionEndpoint, {
          method: 'POST',
          body: { challengeToken: challenge.challengeToken, confirm: true }
        });
        if (completed.status !== 'completed') throw new Error('Login could not be completed.');
        modal.close(true);
        await loadMe();
        if (state.me?.discordUserId) await loadCart();
        notify(typeof successMessage === 'function' ? successMessage(state.me) : successMessage);
        return;
      }
      if (result.status === 'completed') {
        modal.close(true);
        await loadMe();
        if (state.me?.discordUserId) await loadCart();
        notify(typeof successMessage === 'function' ? successMessage(state.me) : successMessage);
        return;
      }
    }
  } catch (error) {
    modal?.close();
    notify(error.message, true);
  }
}

function startMinecraftLogin() {
  return startCodeLogin({
    challengeEndpoint: '/api/auth/challenge',
    completionEndpoint: '/api/auth/complete',
    displayField: 'minecraftName',
    modalOptions: {
      verifyTitle: 'Verify login in Minecraft',
      instructions: 'Join play.corebuilders.gg with the Minecraft account you want to use, then run this command in chat.',
      waitingText: 'Waiting for verification from the creative server…',
      confirmTitle: 'Confirm Minecraft account',
      accountLabel: 'Verified Minecraft account',
      guidanceType: 'minecraft'
    },
    successMessage: (me) => me?.discordUserId
      ? 'Minecraft login successful.'
      : 'Minecraft login successful. Link Discord to unlock marketplace account features.'
  });
}

function startDiscordBotLogin() {
  return startCodeLogin({
    challengeEndpoint: '/api/auth/discord-bot/challenge',
    completionEndpoint: '/api/auth/discord-bot/complete',
    displayField: 'discordName',
    modalOptions: {
      verifyTitle: 'Verify login through Discord',
      instructions: 'In the Core Builders Discord server, run this private slash command with the account you want to use.',
      waitingText: 'Waiting for verification from the Discord bot…',
      confirmTitle: 'Confirm Discord account',
      accountLabel: 'Verified Discord account',
      guidanceType: 'discord'
    },
    successMessage: 'Discord Bot login successful.'
  });
}

function renderAuth(balance) {
  const area = $('#auth-area');
  area.replaceChildren();
  if (!state.me) {
    const register = element('button', 'btn-small btn-flat waves-effect', 'Register');
    register.type = 'button';
    register.addEventListener('click', openRegisterModal);
    const discordLogin = element('button', 'btn-small waves-effect waves-light indigo', 'Log in through Discord Bot');
    discordLogin.type = 'button';
    discordLogin.addEventListener('click', startDiscordBotLogin);
    const minecraftLogin = element('button', 'btn-small waves-effect waves-light deep-purple', 'Log in through Minecraft');
    minecraftLogin.type = 'button';
    minecraftLogin.addEventListener('click', startMinecraftLogin);
    area.append(register, discordLogin, minecraftLogin);
    return;
  }
  const chip = element('div', 'user-chip');
  chip.append(image(state.me.avatarUrl, '', `${state.me.username} avatar`));
  const details = element('div');
  details.append(element('strong', '', state.me.username));
  details.append(element('small', '', shortCoins(balance)));
  chip.append(details);
  const logout = element('button', 'btn-small btn-flat waves-effect', 'Log out');
  logout.type = 'button';
  logout.addEventListener('click', async () => {
    try {
      await api('/api/auth/logout', { method: 'POST' });
      state.me = null;
      state.csrf = null;
      state.shop = null;
      state.myItems = [];
      state.cart = null;
      await loadMe();
      renderCart();
      notify('You have been logged out.');
      showSection('marketplace');
    } catch (error) { notify(error.message, true); }
  });
  area.append(chip);
  if (!state.me.discordUserId) {
    const linkDiscord = element('a', 'btn-small waves-effect waves-light indigo', 'Link Discord');
    linkDiscord.href = '/api/account/discord/link';
    area.append(linkDiscord);
  }
  area.append(logout);
}

function renderAuthenticatedVisibility() {
  const authenticated = Boolean(state.me?.discordUserId);
  $('#shop-guest').classList.toggle('hidden', authenticated);
  $('#shop-dashboard').classList.toggle('hidden', !authenticated);
  $('#cart-guest').classList.toggle('hidden', authenticated);
  $('#cart-panel').classList.toggle('hidden', !authenticated);
  $('#orders-guest').classList.toggle('hidden', authenticated);
  $('#orders-list').classList.toggle('hidden', !authenticated);
  $('#sales-guest').classList.toggle('hidden', authenticated);
  $('#sales-list').classList.toggle('hidden', !authenticated);
}

async function loadCategories() {
  const data = await api('/api/categories');
  state.categories = data.categories || [];
  const select = $('#category-filter');
  const current = select.value;
  select.replaceChildren(new Option('All categories', ''));
  const datalist = $('#category-options');
  datalist.replaceChildren();
  for (const category of state.categories) {
    select.append(new Option(category, category));
    datalist.append(new Option(category, category));
  }
  select.value = current;
}

async function loadItems() {
  const params = new URLSearchParams({
    page: String(state.page),
    pageSize: String(state.pageSize),
    sort: $('#sort-filter').value,
    direction: $('#direction-filter').value
  });
  const query = $('#search-query').value.trim();
  const category = $('#category-filter').value;
  if (query) params.set('q', query);
  if (category) params.set('category', category);
  const data = await api(`/api/items?${params}`);
  state.items = data.items || [];
  state.total = data.total || 0;
  state.page = data.page || 1;
  renderItems();
}

function renderItems() {
  const grid = $('#item-grid');
  grid.replaceChildren();
  $('#result-count').textContent = new Intl.NumberFormat('en-US').format(state.total);
  $('#page-label').textContent = `Page ${state.page}`;
  $('#previous-page').disabled = state.page <= 1;
  $('#next-page').disabled = state.page * state.pageSize >= state.total;
  if (!state.items.length) {
    grid.append(element('div', 'empty-state', 'No listings match these filters.'));
    return;
  }
  for (const item of state.items) grid.append(itemCard(item));
}

function itemCard(item) {
  const card = element('article', 'item-card');
  const media = element('div', 'item-media');
  media.append(image(item.imageUrl, 'item-image', item.name));
  card.append(media);
  const body = element('div', 'item-body');
  const top = element('div', 'item-topline');
  top.append(element('span', 'category-chip', item.category));
  top.append(element('span', 'stock', `${item.stock} in stock`));
  body.append(top, element('h3', '', item.name), element('p', 'item-description', item.description));
  const seller = element('div', 'seller');
  const sellerAvatar = element('span', 'seller-avatar', String(item.shopName || item.sellerUsername || 'C').trim().charAt(0).toUpperCase());
  const sellerDetails = element('span', 'seller-details');
  sellerDetails.append(element('small', '', 'Sold by'));
  sellerDetails.append(element('strong', '', `${item.shopName} · ${item.sellerUsername}`));
  seller.append(sellerAvatar, sellerDetails);
  body.append(seller);
  const footer = element('div', 'item-footer');
  const priceBlock = element('div', 'price-block');
  priceBlock.append(element('small', '', 'Price'));
  priceBlock.append(element('span', 'price', shortCoins(item.price)));
  footer.append(priceBlock);
  const add = element('button', 'btn-small waves-effect waves-light deep-purple', item.stock > 0 ? 'Add to cart' : 'Out of stock');
  add.type = 'button';
  const own = Boolean(item.ownedByCurrentUser);
  const marketplaceAccountReady = Boolean(state.me?.discordUserId);
  add.disabled = !marketplaceAccountReady || item.stock <= 0 || own;
  add.title = !state.me
    ? 'Log in to buy'
    : !state.me.discordUserId
      ? 'Link Discord to buy'
      : own
        ? 'You cannot buy from your own shop'
        : '';
  add.addEventListener('click', () => addToCart(item));
  footer.append(add);
  body.append(footer);
  card.append(body);
  return card;
}

async function addToCart(item) {
  try {
    if (!state.cart) await loadCart();
    const existing = state.cart?.items?.find((line) => line.item.id === item.id)?.quantity || 0;
    state.cart = await api(`/api/cart/items/${item.id}`, { method: 'PUT', body: { quantity: existing + 1 } });
    renderCart();
    notify(`${item.name} added to your cart.`);
  } catch (error) { notify(error.message, true); }
}

async function loadShop() {
  if (!state.me?.discordUserId) return;
  const data = await api('/api/me/shop');
  state.shop = data.shop || null;
  state.myItems = data.items || [];
  renderShop();
}

function renderShop() {
  if (!state.me?.discordUserId) return;
  const status = $('#shop-status');
  $('#item-form').classList.toggle('hidden', !state.shop);
  if (state.shop) {
    $('#shop-name').value = state.shop.name;
    $('#shop-description').value = state.shop.description;
    $('#save-shop').textContent = 'Update shop';
    status.textContent = state.shop.active ? 'Active' : 'Inactive';
    status.classList.toggle('inactive', !state.shop.active);
  } else {
    $('#shop-name').value = '';
    $('#shop-description').value = '';
    $('#save-shop').textContent = 'Create shop';
    status.textContent = 'Not created';
    status.classList.add('inactive');
  }
  updateMaterialFields();
  const list = $('#my-items');
  list.replaceChildren();
  $('#my-listing-count').textContent = `${state.myItems.length} listing${state.myItems.length === 1 ? '' : 's'}`;
  if (!state.myItems.length) {
    list.append(element('div', 'empty-state', state.shop ? 'Create your first listing.' : 'Create your shop first.'));
    return;
  }
  for (const item of state.myItems) {
    const row = element('article', 'management-item');
    row.append(image(item.imageUrl, '', item.name));
    const info = element('div');
    info.append(element('h3', '', item.name));
    info.append(element('p', '', `${item.category} · ${shortCoins(item.price)} · ${item.stock} in stock`));
    if (!item.active) info.append(element('span', 'status-chip inactive', 'Inactive'));
    const actions = element('div', 'management-actions');
    const edit = element('button', 'btn-small btn-flat waves-effect', 'Edit');
    edit.type = 'button';
    edit.addEventListener('click', () => beginItemEdit(item));
    const remove = element('button', 'btn-small waves-effect waves-light red', 'Deactivate');
    remove.type = 'button';
    remove.disabled = !item.active;
    remove.addEventListener('click', () => deactivateItem(item));
    actions.append(edit, remove);
    row.append(info, actions);
    list.append(row);
  }
}

function beginItemEdit(item) {
  clearItemPreviewObjectUrl();
  $('#editing-item-id').value = item.id;
  $('#item-form-title').textContent = 'Edit listing';
  $('#cancel-edit').classList.remove('hidden');
  $('#item-name').value = item.name;
  $('#item-description').value = item.description;
  $('#item-image').value = item.imageUrl || '';
  $('#item-image-file').value = '';
  showItemImagePreview(item.imageUrl || '', 'Current listing image. Choose a file to replace it.');
  $('#item-stock').value = item.stock;
  $('#item-price').value = item.price;
  $('#item-category').value = item.category;
  $('#item-active').checked = item.active;
  updateMaterialFields();
  $('#item-form').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function resetItemForm() {
  clearItemPreviewObjectUrl();
  $('#item-form').reset();
  $('#editing-item-id').value = '';
  $('#item-form-title').textContent = 'Add listing';
  $('#cancel-edit').classList.add('hidden');
  $('#item-stock').value = '1';
  $('#item-price').value = '1';
  $('#item-active').checked = true;
  showItemImagePreview('', '');
  updateMaterialFields();
}

async function deactivateItem(item) {
  const accepted = await showConfirmModal({
    title: 'Deactivate listing?',
    message: `Deactivate “${item.name}”? It will also be removed from active carts.`,
    confirmText: 'Deactivate',
    danger: true
  });
  if (!accepted) return;
  try {
    await api(`/api/me/shop/items/${item.id}`, { method: 'DELETE' });
    await Promise.all([loadShop(), loadItems(), loadCategories()]);
    notify(`${item.name} was deactivated.`);
  } catch (error) { notify(error.message, true); }
}

async function loadCart() {
  if (!state.me?.discordUserId) return;
  state.cart = await api('/api/cart');
  renderCart();
}

function renderCart() {
  const count = state.cart?.itemCount || 0;
  $('#cart-count').textContent = String(count);
  if (!state.me?.discordUserId) return;
  const list = $('#cart-items');
  list.replaceChildren();
  $('#cart-total').textContent = coins(state.cart?.total || 0);
  $('#checkout-button').disabled = !state.cart?.items?.length;
  if (!state.cart?.items?.length) {
    list.append(element('div', 'empty-state', 'Your cart is empty.'));
    return;
  }
  for (const line of state.cart.items) {
    const row = element('article', 'cart-row');
    row.append(image(line.item.imageUrl, '', line.item.name));
    const info = element('div');
    info.append(element('h3', '', line.item.name));
    info.append(element('p', '', `${line.item.shopName} · ${shortCoins(line.item.price)} each · ${line.item.stock} available`));
    const quantity = element('div', 'quantity-control');
    const input = document.createElement('input');
    input.type = 'number';
    input.min = '1';
    input.max = String(Math.min(999, line.item.stock));
    input.value = String(line.quantity);
    input.setAttribute('aria-label', `Quantity for ${line.item.name}`);
    const update = element('button', 'btn-small btn-flat waves-effect', 'Update');
    update.type = 'button';
    update.addEventListener('click', async () => {
      try {
        state.cart = await api(`/api/cart/items/${line.item.id}`, { method: 'PUT', body: { quantity: Number(input.value) } });
        renderCart();
      } catch (error) { notify(error.message, true); }
    });
    const remove = element('button', 'text-button', 'Remove');
    remove.type = 'button';
    remove.addEventListener('click', async () => {
      try {
        state.cart = await api(`/api/cart/items/${line.item.id}`, { method: 'DELETE' });
        renderCart();
      } catch (error) { notify(error.message, true); }
    });
    quantity.append(input, update, remove);
    row.append(info, quantity, element('strong', '', shortCoins(line.lineTotal)));
    list.append(row);
  }
}

async function checkout() {
  const accepted = await showConfirmModal({
    title: 'Confirm checkout',
    message: `Spend ${coins(state.cart?.total || 0)} and place this order?`,
    confirmText: 'Place order'
  });
  if (!accepted) return;
  const button = $('#checkout-button');
  button.disabled = true;
  try {
    const checkoutRequest = {
      expectedTotal: state.cart.total,
      items: state.cart.items.map((line) => ({
        itemId: line.item.id,
        quantity: line.quantity,
        unitPrice: line.item.price,
        version: line.item.version
      }))
    };
    const order = await api('/api/cart/checkout', { method: 'POST', body: checkoutRequest });
    notify(`Order placed successfully for ${coins(order.totalPrice)}.`);
    await Promise.all([loadMe(), loadCart(), loadItems(), loadOrders()]);
    showSection('orders');
  } catch (error) {
    notify(error.message, true);
    button.disabled = false;
  }
}

async function loadOrders() {
  if (!state.me?.discordUserId) return;
  const data = await api('/api/orders?limit=25');
  renderOrders(data.orders || []);
}

function renderOrders(orders) {
  const list = $('#orders-list');
  list.replaceChildren();
  if (!orders.length) {
    list.append(element('div', 'empty-state', 'You have not placed any marketplace orders.'));
    return;
  }
  for (const order of orders) {
    const card = element('article', 'order-card');
    const head = element('div', 'order-head');
    const title = element('div');
    title.append(element('h3', '', `Order ${order.id.slice(0, 8)}`));
    title.append(element('span', 'muted', dateTime(order.createdAt)));
    head.append(title, element('strong', '', shortCoins(order.totalPrice)), element('span', 'status-chip', order.status));
    card.append(head);
    const lines = element('div', 'order-lines');
    for (const line of order.lines || []) {
      const row = element('div', 'order-line');
      const info = element('div');
      info.append(element('strong', '', `${line.quantity} × ${line.itemName}`));
      info.append(element('div', 'muted', `${line.shopName} · ${line.status.replaceAll('_', ' ')}`));
      row.append(info, element('span', '', shortCoins(line.lineTotal)));

      if (line.status === 'PENDING_DELIVERY') {
        const actions = element('div', 'management-actions');
        const delivered = element('button', 'btn-small waves-effect waves-light deep-purple', 'Mark delivered');
        delivered.type = 'button';
        delivered.addEventListener('click', async () => {
          const accepted = await showConfirmModal({
            title: 'Mark order delivered?',
            message: `Confirm that ${line.itemName} was delivered/received. The seller must confirm before payment is released.`,
            confirmText: 'Mark delivered'
          });
          if (!accepted) return;
          try {
            await api(`/api/orders/${line.id}/delivered`, { method: 'POST' });
            await loadOrders();
            notify('Delivery marked. The seller must now confirm it.');
          } catch (error) { notify(error.message, true); }
        });

        const cancel = element('button', 'btn-small btn-flat waves-effect', 'Cancel');
        cancel.type = 'button';
        cancel.addEventListener('click', async () => {
          const accepted = await showConfirmModal({
            title: 'Cancel order item?',
            message: `Cancel ${line.itemName} and refund ${shortCoins(line.lineTotal)}?`,
            confirmText: 'Cancel and refund',
            danger: true
          });
          if (!accepted) return;
          try {
            await api(`/api/orders/${line.id}/cancel`, { method: 'POST' });
            await Promise.all([loadOrders(), loadMe(), loadItems()]);
            notify('Order line cancelled and refunded.');
          } catch (error) { notify(error.message, true); }
        });
        actions.append(delivered, cancel);
        row.append(actions);
      } else if (line.status === 'DELIVERED' && !line.fundsReleased) {
        const dispute = element('button', 'btn-small btn-flat waves-effect', 'Report problem');
        dispute.type = 'button';
        dispute.addEventListener('click', async () => {
          const reason = await showPromptModal({
            title: 'Report a delivery problem',
            message: `Describe the problem with ${line.itemName}. Staff will review the dispute.`,
            confirmText: 'Submit dispute',
            input: {
              label: 'Problem description',
              help: 'Enter 5–500 characters. Do not include passwords or private coordinates.',
              placeholder: 'Explain what was not delivered or what went wrong…',
              minLength: 5,
              maxLength: 500
            }
          });
          if (!reason) return;
          try {
            await api(`/api/orders/${line.id}/dispute`, { method: 'POST', body: { reason } });
            await loadOrders();
            notify('The order line is now disputed. Staff review is required.');
          } catch (error) { notify(error.message, true); }
        });
        row.append(dispute);
      }
      lines.append(row);
    }
    card.append(lines);
    list.append(card);
  }
}

async function loadSales() {
  if (!state.me?.discordUserId) return;
  const data = await api('/api/sales?limit=50');
  renderSales(data.sales || []);
}

function renderSales(sales) {
  const list = $('#sales-list');
  list.replaceChildren();
  if (!sales.length) {
    list.append(element('div', 'empty-state', 'No one has purchased from your shop yet.'));
    return;
  }
  for (const sale of sales) {
    const card = element('article', 'order-card');
    const head = element('div', 'order-head');
    const info = element('div');
    info.append(element('h3', '', `${sale.quantity} × ${sale.itemName}`));
    info.append(element('div', 'muted', `Buyer: ${sale.buyerUsername} · ${dateTime(sale.createdAt)}`));
    head.append(info, element('strong', '', shortCoins(sale.lineTotal)));
    const status = element('span', `status-chip${sale.status === 'DELIVERED' ? '' : ' inactive'}`, sale.status.replaceAll('_', ' '));
    head.append(status);
    card.append(head);

    if (sale.status === 'PENDING_DELIVERY') {
      const cancel = element('button', 'btn-small btn-flat waves-effect', 'Cancel order');
      cancel.type = 'button';
      cancel.addEventListener('click', async () => {
        const accepted = await showConfirmModal({
          title: 'Cancel this sale?',
          message: `Cancel ${sale.itemName}? The buyer will be refunded and stock will be restored.`,
          confirmText: 'Cancel and refund',
          danger: true
        });
        if (!accepted) return;
        try {
          await api(`/api/sales/${sale.id}/cancel`, { method: 'POST' });
          await Promise.all([loadSales(), loadMe(), loadItems()]);
          notify('Sale cancelled. The buyer was refunded.');
        } catch (error) { notify(error.message, true); }
      });
      card.append(cancel);
    } else if (sale.status === 'DELIVERED' && !sale.fundsReleased) {
      const confirm = element('button', 'btn-small waves-effect waves-light deep-purple', 'Confirm delivery');
      confirm.type = 'button';
      confirm.addEventListener('click', async () => {
        const accepted = await showConfirmModal({
          title: 'Confirm completed delivery?',
          message: `Confirm delivery of ${sale.itemName}. The escrowed coins will be released to you.`,
          confirmText: 'Confirm and release coins'
        });
        if (!accepted) return;
        try {
          await api(`/api/sales/${sale.id}/confirm`, { method: 'POST' });
          await Promise.all([loadSales(), loadMe()]);
          notify('Delivery confirmed and escrow released.');
        } catch (error) { notify(error.message, true); }
      });
      card.append(confirm);
    }
    list.append(card);
  }
}

function showSection(name) {
  $$('.view').forEach((view) => view.classList.toggle('active', view.id === name));
  $$('.nav-link').forEach((button) => button.classList.toggle('active', button.dataset.section === name));
  window.history.replaceState(null, '', `#${name}`);
  if (name === 'shop') loadShop().catch((error) => notify(error.message, true));
  if (name === 'cart') loadCart().catch((error) => notify(error.message, true));
  if (name === 'orders') loadOrders().catch((error) => notify(error.message, true));
  if (name === 'sales') loadSales().catch((error) => notify(error.message, true));
}

function bindEvents() {
  $$('.nav-link').forEach((button) => button.addEventListener('click', () => showSection(button.dataset.section)));
  $('#search-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    state.page = 1;
    try { await loadItems(); } catch (error) { notify(error.message, true); }
  });
  $('#reset-filters').addEventListener('click', async () => {
    $('#search-query').value = '';
    $('#category-filter').value = '';
    $('#sort-filter').value = 'newest';
    $('#direction-filter').value = 'desc';
    state.page = 1;
    updateMaterialFields();
    try { await loadItems(); } catch (error) { notify(error.message, true); }
  });
  $('#previous-page').addEventListener('click', async () => { state.page = Math.max(1, state.page - 1); await loadItems(); });
  $('#next-page').addEventListener('click', async () => { state.page += 1; await loadItems(); });
  $('#checkout-button').addEventListener('click', checkout);
  $('#cancel-edit').addEventListener('click', resetItemForm);
  $('#remove-item-image').addEventListener('click', clearItemImage);
  $('#item-image-file').addEventListener('change', (event) => {
    clearItemPreviewObjectUrl();
    const file = event.target.files?.[0];
    if (!file) {
      showItemImagePreview($('#item-image').value, 'Current listing image.');
      return;
    }
    itemPreviewObjectUrl = URL.createObjectURL(file);
    showItemImagePreview(itemPreviewObjectUrl, 'It will be uploaded when the listing is saved.');
  });

  $('#shop-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const body = { name: $('#shop-name').value, description: $('#shop-description').value };
    try {
      const existed = Boolean(state.shop);
      await api('/api/me/shop', { method: existed ? 'PUT' : 'POST', body });
      await loadShop();
      notify(existed ? 'Shop saved.' : 'Shop created.');
    } catch (error) { notify(error.message, true); }
  });

  $('#item-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const id = $('#editing-item-id').value;
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    try {
      let imageUrl = $('#item-image').value;
      const imageFile = $('#item-image-file').files?.[0];
      if (imageFile) {
        const uploaded = await uploadListingImage(imageFile);
        imageUrl = uploaded.url;
        $('#item-image').value = imageUrl;
        clearItemPreviewObjectUrl();
        $('#item-image-file').value = '';
        showItemImagePreview(imageUrl, 'Image uploaded and ready to save.');
      }
      const body = {
        name: $('#item-name').value,
        description: $('#item-description').value,
        imageUrl,
        stock: Number($('#item-stock').value),
        price: Number($('#item-price').value),
        category: $('#item-category').value,
        active: $('#item-active').checked
      };
      await api(id ? `/api/me/shop/items/${id}` : '/api/me/shop/items', { method: id ? 'PUT' : 'POST', body });
      resetItemForm();
      await Promise.all([loadShop(), loadItems(), loadCategories()]);
      notify(id ? 'Listing updated.' : 'Listing created.');
    } catch (error) {
      notify(error.message, true);
    } finally {
      submitButton.disabled = false;
    }
  });
}

function handleLoginResult() {
  const url = new URL(window.location.href);
  const result = url.searchParams.get('discord');
  if (!result) return;
  if (result === 'linked') notify('Discord account linked successfully.');
  else {
    const code = url.searchParams.get('code') || 'link_failed';
    notify(`Discord linking failed: ${code.replaceAll('_', ' ')}`, true);
  }
  url.searchParams.delete('discord');
  url.searchParams.delete('code');
  window.history.replaceState(null, '', url.pathname + url.hash);
}

async function bootstrap() {
  initMaterialize();
  bindEvents();
  handleLoginResult();
  try {
    await loadMe();
    await Promise.all([loadCategories(), loadItems()]);
    if (state.me?.discordUserId) await loadCart();
    const initial = window.location.hash.slice(1);
    showSection(['marketplace', 'shop', 'cart', 'orders', 'sales'].includes(initial) ? initial : 'marketplace');
  } catch (error) {
    notify(error.message, true);
  }
}

bootstrap();
