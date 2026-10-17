// „Trage în jos pentru reîncărcare” în aplicația de pe ecranul principal al iPhone / iPad.
// În modul aplicație (standalone), iOS nu are gestul nativ, spre deosebire de Safari și de Android.
// Tot aici: la revenirea în aplicație după o pauză lungă, pagina se reîncarcă (iOS o păstrează veche).
(function () {
  if (window.navigator.standalone !== true) {
    return;
  }
  var THRESHOLD = 70;   // cât trebuie tras (px) ca pagina să se reîncarce
  var MAX = 110;
  var STALE_MS = 5 * 60 * 1000;

  var indicator = document.createElement('div');
  indicator.setAttribute('aria-hidden', 'true');
  indicator.textContent = '↻';
  indicator.style.cssText = 'position:fixed;left:50%;top:0;z-index:10000;width:40px;height:40px;margin-left:-20px;'
      + 'border-radius:50%;background:#fff;color:#0B2A63;box-shadow:0 2px 8px rgba(0,0,0,.25);'
      + 'display:flex;align-items:center;justify-content:center;font:600 22px/1 system-ui,sans-serif;'
      + 'transform:translateY(-50px);opacity:0;pointer-events:none;transition:none;';
  var spin = document.createElement('style');
  spin.textContent = '@keyframes ttm-ptr-spin{to{transform:translateY(60px) rotate(360deg)}}';
  document.head.appendChild(spin);
  document.addEventListener('DOMContentLoaded', function () {
    document.body.appendChild(indicator);
  });

  var startY = null;
  var pull = 0;

  function atTop(event) {
    var root = document.scrollingElement || document.documentElement;
    if (root.scrollTop > 0) {
      return false;
    }
    var path = event.composedPath ? event.composedPath() : [];
    for (var i = 0; i < path.length; i++) {
      var el = path[i];
      if (el && el.nodeType === 1 && el.scrollTop > 0) {
        return false;
      }
    }
    return true;
  }

  function dialogOpen() {
    return document.querySelector('vaadin-dialog-overlay[opened], vaadin-confirm-dialog-overlay[opened],'
        + ' vaadin-select-overlay[opened], vaadin-date-picker-overlay[opened]') !== null;
  }

  function show(distance) {
    indicator.style.transition = 'none';
    indicator.style.opacity = String(Math.min(1, distance / THRESHOLD));
    indicator.style.transform = 'translateY(' + (distance - 50) + 'px) rotate(' + (distance * 3) + 'deg)';
  }

  function hide() {
    indicator.style.transition = 'transform .2s, opacity .2s';
    indicator.style.opacity = '0';
    indicator.style.transform = 'translateY(-50px)';
  }

  document.addEventListener('touchstart', function (e) {
    startY = (e.touches.length === 1 && !dialogOpen() && atTop(e)) ? e.touches[0].clientY : null;
    pull = 0;
  }, { passive: true });

  document.addEventListener('touchmove', function (e) {
    if (startY === null) {
      return;
    }
    var dy = e.touches[0].clientY - startY;
    if (dy <= 0) {
      pull = 0;
      hide();
      return;
    }
    pull = Math.min(MAX, dy * 0.6);
    show(pull);
  }, { passive: true });

  document.addEventListener('touchend', function () {
    if (startY === null) {
      return;
    }
    startY = null;
    if (pull >= THRESHOLD) {
      indicator.style.transition = 'none';
      indicator.style.opacity = '1';
      indicator.style.animation = 'ttm-ptr-spin .8s linear infinite';
      window.location.reload();
    } else {
      hide();
    }
    pull = 0;
  });

  var hiddenAt = null;
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') {
      hiddenAt = Date.now();
    } else if (hiddenAt !== null && Date.now() - hiddenAt > STALE_MS && !dialogOpen()) {
      window.location.reload();
    }
  });
})();
