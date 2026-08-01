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
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 400"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#272e40"/><stop offset="1" stop-color="#111620"/></linearGradient></defs><rect width="640" height="400" fill="url(#g)"/><path d="M245 145h150v110H245z" fill="none" stroke="#7c5cff" stroke-width="14"/><path d="m245 190 75-45 75 45-75 45z" fill="none" stroke="#4d8dff" stroke-width="14"/></svg>'
);

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));
const jq = window.jQuery;
let itemPreviewObjectUrl = null;

function requireJQuery() {
  if (!jq) throw new Error('The website modal library could not be loaded. Refresh the page and try again.');
  return jq;
}

function setModalOpen(open) {
  document.body.classList.toggle('modal-open', open);
}

function showActionModal({
  title,
  message,
  confirmText = 'Continue',
  cancelText = 'Cancel',
  danger = false,
  input = null
}) {
  const $jq = requireJQuery();
  const $modal = $jq('#action-modal');
  const previousFocus = document.activeElement;

  $jq('#action-modal-title').text(title || 'Please confirm');
  $jq('#action-modal-message').text(message || '');
  $jq('#action-modal-confirm')
    .text(confirmText)
    .toggleClass('danger', Boolean(danger))
    .toggleClass('primary', !danger);
  $jq('#action-modal-cancel').text(cancelText);
  $jq('#action-modal-error').addClass('hidden').text('');

  const $inputGroup = $jq('#action-modal-input-group');
  const $input = $jq('#action-modal-input');
  if (input) {
    $inputGroup.removeClass('hidden');
    $jq('#action-modal-input-label').text(input.label || 'Details');
    $jq('#action-modal-input-help').text(input.help || '');
    $input
      .val(input.value || '')
      .attr('minlength', input.minLength || 0)
      .attr('maxlength', input.maxLength || 500)
      .attr('placeholder', input.placeholder || '');
  } else {
    $inputGroup.addClass('hidden');
    $input.val('').removeAttr('minlength maxlength placeholder');
  }

  return new Promise((resolve) => {
    let settled = false;
    const finish = (value) => {
      if (settled) return;
      settled = true;
      $modal.addClass('hidden').attr('aria-hidden', 'true').off('.coreModal');
      $jq(document).off('.coreModal');
      $jq('#action-modal-confirm, #action-modal-cancel, #action-modal-close').off('.coreModal');
      setModalOpen(false);
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
      resolve(value);
    };

    const cancel = () => finish(input ? null : false);
    const accept = () => {
      if (!input) {
        finish(true);
        return;
      }
      const value = String($input.val() || '').trim();
      const minLength = input.minLength || 0;
      const maxLength = input.maxLength || 500;
      if (value.length < minLength || value.length > maxLength) {
        $jq('#action-modal-error')
          .text(`Enter between ${minLength} and ${maxLength} characters.`)
          .removeClass('hidden');
        $input.trigger('focus');
        return;
      }
      finish(value);
    };

    $jq('#action-modal-confirm').on('click.coreModal', accept);
    $jq('#action-modal-cancel, #action-modal-close').on('click.coreModal', cancel);
    $modal.on('mousedown.coreModal', (event) => {
      if (event.target === $modal.get(0)) cancel();
    });
    $jq(document).on('keydown.coreModal', (event) => {
      if (event.key === 'Escape') cancel();
      if (event.key === 'Enter' && !input && !$jq(event.target).is('textarea')) accept();
    });

    $modal.removeClass('hidden').attr('aria-hidden', 'false');
    setModalOpen(true);
    window.setTimeout(() => (input ? $input : $jq('#action-modal-confirm')).trigger('focus'), 0);
  });
}

function showConfirmModal(options) {
  return showActionModal(options);
}

function showPromptModal(options) {
  return showActionModal(options);
}

function openCodeLoginModal({
  verifyTitle,
  instructions,
  waitingText,
  confirmTitle,
  accountLabel
}) {
  const $jq = requireJQuery();
  const $modal = $jq('#minecraft-login-modal');
  const previousFocus = document.activeElement;
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
    $modal.addClass('hidden').attr('aria-hidden', 'true').off('.codeLogin');
    $jq(document).off('.codeLogin');
    $jq('#minecraft-login-cancel, #minecraft-login-close, #minecraft-login-confirm').off('.codeLogin');
    setModalOpen(false);
    if (previousFocus instanceof HTMLElement) previousFocus.focus();
  };

  $jq('#minecraft-login-title').text(verifyTitle);
  $jq('#minecraft-login-status').text('Creating a one-time login code…');
  $jq('#minecraft-login-code').text('--------');
  $jq('#minecraft-login-command').text('');
  $jq('#minecraft-login-instructions').text(instructions);
  $jq('#minecraft-login-account-label').text(accountLabel);
  $jq('#minecraft-login-challenge').removeClass('hidden');
  $jq('#minecraft-login-account').addClass('hidden');
  $jq('#minecraft-login-confirm').prop('disabled', false).text('Continue').addClass('hidden');
  $jq('#minecraft-login-cancel').text('Cancel');

  $jq('#minecraft-login-cancel, #minecraft-login-close').on('click.codeLogin', () => close(false));
  $modal.on('mousedown.codeLogin', (event) => {
    if (event.target === $modal.get(0)) close(false);
  });
  $jq(document).on('keydown.codeLogin', (event) => {
    if (event.key === 'Escape') close(false);
  });

  $modal.removeClass('hidden').attr('aria-hidden', 'false');
  setModalOpen(true);
  window.setTimeout(() => $jq('#minecraft-login-cancel').trigger('focus'), 0);

  return {
    isOpen: () => !closed,
    showChallenge(challenge) {
      if (closed) return;
      $jq('#minecraft-login-code').text(challenge.code);
      $jq('#minecraft-login-command').text(challenge.command);
      $jq('#minecraft-login-status').text(waitingText);
    },
    confirmAccount(displayName) {
      if (closed) return Promise.resolve(false);
      $jq('#minecraft-login-title').text(confirmTitle);
      $jq('#minecraft-login-status').text('The login code was verified successfully.');
      $jq('#minecraft-login-challenge').addClass('hidden');
      $jq('#minecraft-login-name').text(displayName);
      $jq('#minecraft-login-account').removeClass('hidden');
      $jq('#minecraft-login-confirm').removeClass('hidden');
      $jq('#minecraft-login-cancel').text('Use another account');
      return new Promise((resolve) => {
        decisionResolver = resolve;
        $jq('#minecraft-login-confirm')
          .off('click.codeLoginConfirm')
          .on('click.codeLoginConfirm', () => {
            finishDecision(true);
            $jq('#minecraft-login-confirm').prop('disabled', true).text('Signing in…');
          })
          .trigger('focus');
      });
    },
    close
  };
}
const points = (value) => `${new Intl.NumberFormat('en-US').format(value || 0)} contribution points`;
const shortPoints = (value) => `${new Intl.NumberFormat('en-US').format(value || 0)} CP`;
const dateTime = (value) => value ? new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—';

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
  const $jq = requireJQuery();
  const data = new FormData();
  data.append('image', file, file.name);
  $('#item-image-upload-status').textContent = 'Uploading image…';

  return new Promise((resolve, reject) => {
    $jq.ajax({
      url: '/api/me/shop/images',
      method: 'POST',
      data,
      processData: false,
      contentType: false,
      dataType: 'json',
      headers: {
        Accept: 'application/json',
        'X-CSRF-Token': state.csrf || ''
      },
      xhr() {
        const xhr = $jq.ajaxSettings.xhr();
        if (xhr.upload) {
          xhr.upload.addEventListener('progress', (event) => {
            if (!event.lengthComputable) return;
            const percent = Math.max(1, Math.round((event.loaded / event.total) * 100));
            $('#item-image-upload-status').textContent = `Uploading image… ${percent}%`;
          });
        }
        return xhr;
      }
    }).done((response) => {
      $('#item-image-upload-status').textContent = 'Image uploaded.';
      resolve(response);
    }).fail((xhr) => {
      let message = `Image upload failed with status ${xhr.status}`;
      try {
        const payload = xhr.responseJSON || JSON.parse(xhr.responseText || '{}');
        message = payload?.error?.message || message;
      } catch (_) {
        // Keep the status-based fallback.
      }
      $('#item-image-upload-status').textContent = 'Image upload failed.';
      reject(new Error(message));
    });
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
      instructions: 'Join the Minecraft server with the account you want to use, then run this command in chat.',
      waitingText: 'Waiting for verification from the game…',
      confirmTitle: 'Confirm Minecraft account',
      accountLabel: 'Verified Minecraft account'
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
      accountLabel: 'Verified Discord account'
    },
    successMessage: 'Discord Bot login successful.'
  });
}

function renderAuth(balance) {
  const area = $('#auth-area');
  area.replaceChildren();
  if (!state.me) {
    const discordLogin = element('button', 'button discord', 'Log in through Discord Bot');
    discordLogin.type = 'button';
    discordLogin.addEventListener('click', startDiscordBotLogin);
    const minecraftLogin = element('button', 'button primary', 'Log in through Minecraft');
    minecraftLogin.type = 'button';
    minecraftLogin.addEventListener('click', startMinecraftLogin);
    area.append(discordLogin, minecraftLogin);
    return;
  }
  const chip = element('div', 'user-chip');
  chip.append(image(state.me.avatarUrl, '', `${state.me.username} avatar`));
  const details = element('div');
  details.append(element('strong', '', state.me.username));
  details.append(element('small', '', shortPoints(balance)));
  chip.append(details);
  const logout = element('button', 'button ghost', 'Log out');
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
    const linkDiscord = element('a', 'button discord', 'Link Discord');
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
  card.append(image(item.imageUrl, 'item-image', item.name));
  const body = element('div', 'item-body');
  const top = element('div', 'item-topline');
  top.append(element('span', 'category-chip', item.category));
  top.append(element('span', 'stock', `${item.stock} in stock`));
  body.append(top, element('h3', '', item.name), element('p', 'item-description', item.description));
  const seller = element('p', 'seller', `${item.shopName} · ${item.sellerUsername}`);
  body.append(seller);
  const footer = element('div', 'item-footer');
  footer.append(element('span', 'price', shortPoints(item.price)));
  const add = element('button', 'button primary', item.stock > 0 ? 'Add to cart' : 'Out of stock');
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
    info.append(element('p', '', `${item.category} · ${shortPoints(item.price)} · ${item.stock} in stock`));
    if (!item.active) info.append(element('span', 'status-chip inactive', 'Inactive'));
    const actions = element('div', 'management-actions');
    const edit = element('button', 'button ghost', 'Edit');
    edit.type = 'button';
    edit.addEventListener('click', () => beginItemEdit(item));
    const remove = element('button', 'button danger', 'Deactivate');
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
  $('#cart-total').textContent = points(state.cart?.total || 0);
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
    info.append(element('p', '', `${line.item.shopName} · ${shortPoints(line.item.price)} each · ${line.item.stock} available`));
    const quantity = element('div', 'quantity-control');
    const input = document.createElement('input');
    input.type = 'number';
    input.min = '1';
    input.max = String(Math.min(999, line.item.stock));
    input.value = String(line.quantity);
    input.setAttribute('aria-label', `Quantity for ${line.item.name}`);
    const update = element('button', 'button ghost', 'Update');
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
    row.append(info, quantity, element('strong', '', shortPoints(line.lineTotal)));
    list.append(row);
  }
}

async function checkout() {
  const accepted = await showConfirmModal({
    title: 'Confirm checkout',
    message: `Spend ${points(state.cart?.total || 0)} and place this order?`,
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
    notify(`Order placed successfully for ${points(order.totalPrice)}.`);
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
    head.append(title, element('strong', '', shortPoints(order.totalPrice)), element('span', 'status-chip', order.status));
    card.append(head);
    const lines = element('div', 'order-lines');
    for (const line of order.lines || []) {
      const row = element('div', 'order-line');
      const info = element('div');
      info.append(element('strong', '', `${line.quantity} × ${line.itemName}`));
      info.append(element('div', 'muted', `${line.shopName} · ${line.status.replaceAll('_', ' ')}`));
      const amount = element('span', '', shortPoints(line.lineTotal));
      row.append(info, amount);
      if (line.status === 'PENDING_DELIVERY') {
        const cancel = element('button', 'button ghost', 'Cancel');
        cancel.type = 'button';
        cancel.addEventListener('click', async () => {
          const accepted = await showConfirmModal({
            title: 'Cancel order item?',
            message: `Cancel ${line.itemName} and refund ${shortPoints(line.lineTotal)}?`,
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
        row.append(cancel);
      } else if (line.status === 'DELIVERED' && !line.fundsReleased) {
        const actions = element('div', 'management-actions');
        const confirm = element('button', 'button primary', 'Confirm received');
        confirm.type = 'button';
        confirm.addEventListener('click', async () => {
          const accepted = await showConfirmModal({
            title: 'Confirm delivery',
            message: `Confirm that you received ${line.itemName}? This releases payment to the seller.`,
            confirmText: 'Confirm received'
          });
          if (!accepted) return;
          try {
            await api(`/api/orders/${line.id}/confirm`, { method: 'POST' });
            await Promise.all([loadOrders(), loadMe()]);
            notify('Delivery confirmed and seller paid.');
          } catch (error) { notify(error.message, true); }
        });
        const dispute = element('button', 'button ghost', 'Report problem');
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
        actions.append(confirm, dispute);
        row.append(actions);
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
    head.append(info, element('strong', '', shortPoints(sale.lineTotal)));
    const status = element('span', `status-chip${sale.status === 'DELIVERED' ? '' : ' inactive'}`, sale.status.replaceAll('_', ' '));
    head.append(status);
    card.append(head);
    if (sale.status === 'PENDING_DELIVERY') {
      const actions = element('div', 'management-actions');
      actions.style.marginTop = '12px';
      const delivered = element('button', 'button primary', 'Mark delivered');
      delivered.type = 'button';
      delivered.addEventListener('click', async () => {
        try {
          await api(`/api/sales/${sale.id}/delivered`, { method: 'POST' });
          await loadSales();
          notify(`${sale.itemName} marked as delivered.`);
        } catch (error) { notify(error.message, true); }
      });
      actions.append(delivered);
      card.append(actions);
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
