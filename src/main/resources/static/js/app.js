const $ = id => document.getElementById(id);
let map = null;
let incidentData = [];
let communityReportsData = [];
let savedCommutesData = [];
let nextCommuteData = null;
let selectedLocations = { origin: null, destination: null };
let searchTimers = { origin: null, destination: null };
let lastSearches = { origin: '', destination: '' };
let searchCenter = null;
let currentRoutes = [];
let activeRouteIndex = 0;
let incidentMarkers = [];
let reportMarkers = [];
let currentPlannedPlan = null;

function setStatus(element, status) {
  if (!element) return;
  const s = (status || 'UNKNOWN').toUpperCase();
  element.textContent = s;
  element.className = 'status ' + (s === 'LIVE' || s === 'READY' ? 'ready' : 'unavailable');
}

function updateOfflineStatus() {
  const banner = $('offline-banner');
  if (banner) {
    banner.style.display = navigator.onLine ? 'none' : 'block';
  }
}
window.addEventListener('online', updateOfflineStatus);
window.addEventListener('offline', updateOfflineStatus);

// ============================================================================
// DEVICE LOCATION & AUTOCOMPLETE SEARCH
// ============================================================================

function requestDeviceLocation(onSuccess, onError) {
  if (!navigator.geolocation) {
    if (onError) onError('Geolocation is not supported by your browser.');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    position => {
      onSuccess({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude
      });
    },
    err => {
      const msg = err.code === 1 ? 'Location permission was denied.' : 'Device location could not be determined.';
      if (onError) onError(msg);
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
  );
}

function setupLocationSearch(kind) {
  const input = $(`${kind}-query`);
  const suggestions = $(`${kind}-suggestions`);
  if (!input || !suggestions) return;

  input.addEventListener('input', () => {
    clearTimeout(searchTimers[kind]);
    searchTimers[kind] = setTimeout(() => searchLocations(kind), 300);
  });

  input.addEventListener('keydown', e => {
    if (e.key === 'Escape') suggestions.innerHTML = '';
  });
}

async function searchLocations(kind) {
  const input = $(`${kind}-query`);
  const suggestions = $(`${kind}-suggestions`);
  const query = input.value.trim();
  if (query.length < 2 || query === lastSearches[kind]) {
    if (query.length < 2) suggestions.innerHTML = '';
    return;
  }
  lastSearches[kind] = query;
  suggestions.innerHTML = '<div class="suggestion">Searching locations…</div>';

  try {
    const center = selectedLocations.origin || searchCenter;
    const locationBias = center ? `&latitude=${center.latitude}&longitude=${center.longitude}` : '';
    const response = await fetch(`/api/v1/places/search?query=${encodeURIComponent(query)}${locationBias}`)
      .then(r => r.json());

    if (response.status !== 'LIVE' || !response.data?.results) {
      suggestions.innerHTML = `<div class="suggestion">${response.reason || 'No locations found.'}</div>`;
      return;
    }

    const results = response.data.results;
    if (results.length === 0) {
      suggestions.innerHTML = '<div class="suggestion">No matching places found.</div>';
      return;
    }

    suggestions.innerHTML = results.slice(0, 5).map((r, i) => {
      const title = r.poi?.name || r.address?.freeformAddress || 'Known place';
      const subtitle = r.address?.freeformAddress && r.poi?.name ? r.address.freeformAddress : '';
      return `<button type="button" class="suggestion" data-index="${i}">
        <strong>${title}</strong>${subtitle ? `<br><small class="muted">${subtitle}</small>` : ''}
      </button>`;
    }).join('');

    suggestions.querySelectorAll('button').forEach(btn => {
      btn.addEventListener('click', () => {
        const idx = parseInt(btn.getAttribute('data-index'), 10);
        const item = results[idx];
        if (item?.position) {
          selectedLocations[kind] = {
            name: item.poi?.name || item.address?.freeformAddress || query,
            latitude: item.position.lat,
            longitude: item.position.lon
          };
          input.value = selectedLocations[kind].name;
          suggestions.innerHTML = '';
        }
      });
    });
  } catch {
    suggestions.innerHTML = '<div class="suggestion">Location service could not be reached.</div>';
  }
}

// ============================================================================
// QUICK VISAKHAPATNAM LANDMARK SHORTCUTS
// ============================================================================

function initLandmarkChips() {
  document.querySelectorAll('.landmark-chips .chip').forEach(chip => {
    chip.addEventListener('click', () => {
      const name = chip.getAttribute('data-name');
      const lat = parseFloat(chip.getAttribute('data-lat'));
      const lon = parseFloat(chip.getAttribute('data-lon'));

      // Highlight active chip
      document.querySelectorAll('.landmark-chips .chip').forEach(c => c.style.borderColor = '');
      chip.style.borderColor = '#49d3a7';

      selectedLocations.destination = { name, latitude: lat, longitude: lon };
      const destInput = $('destination-query');
      if (destInput) {
        destInput.value = name;
        // On mobile, gently scroll to input if needed
        if (window.innerWidth <= 768) {
          destInput.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
      }
      $('destination-suggestions').innerHTML = '';
    });
  });
}

// ============================================================================
// FLOWCAST 2.0: DEPARTURE RECOMMENDATION ENGINE
// ============================================================================

async function calculateCommuteDeparture(e) {
  e.preventDefault();
  if (!selectedLocations.origin || !selectedLocations.destination) {
    alert('Please select valid origin and destination locations from the suggestions.');
    return;
  }

  const arrivalTime = $('target-arrival-time').value;
  const safetyBuffer = parseInt($('safety-buffer').value, 10);

  const payload = {
    originName: selectedLocations.origin.name || $('origin-query').value,
    originLatitude: selectedLocations.origin.latitude,
    originLongitude: selectedLocations.origin.longitude,
    destinationName: selectedLocations.destination.name || $('destination-query').value,
    destinationLatitude: selectedLocations.destination.latitude,
    destinationLongitude: selectedLocations.destination.longitude,
    arrivalTime: arrivalTime,
    safetyBufferMinutes: safetyBuffer
  };

  $('planner-status').textContent = 'Calculating…';
  $('departure-result-panel').style.display = 'block';
  $('rec-leave-time').textContent = 'CALCULATING…';

  try {
    const [planRes, routeRes] = await Promise.all([
      fetch('/api/v1/departures/calculate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }).then(r => r.json()),
      fetch('/api/v1/routes/alternatives', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          origin: { latitude: payload.originLatitude, longitude: payload.originLongitude },
          destination: { latitude: payload.destinationLatitude, longitude: payload.destinationLongitude }
        })
      }).then(r => r.json())
    ]);

    if (planRes.status === 'LIVE' && planRes.data) {
      currentPlannedPlan = planRes.data;
      renderDeparturePlanResult(planRes.data);
      $('planner-status').textContent = 'Departure Calculated';
      $('planner-status').className = 'status ready';
    } else {
      $('rec-leave-time').textContent = 'UNAVAILABLE';
      $('rec-headline-sentence').textContent = planRes.reason || 'Could not calculate departure recommendation.';
      $('planner-status').textContent = 'Failed';
      $('planner-status').className = 'status unavailable';
    }

    if (routeRes.status === 'LIVE' && routeRes.data?.routes) {
      currentRoutes = routeRes.data.routes;
      renderRouteAlternatives(currentRoutes);
      renderRouteOnMap(0);
    }
  } catch (err) {
    console.error('Departure calculation error:', err);
    $('rec-leave-time').textContent = 'ERROR';
    $('rec-headline-sentence').textContent = 'Could not reach server to calculate departure time.';
    $('planner-status').className = 'status unavailable';
  }
}

function renderDeparturePlanResult(plan) {
  $('departure-result-panel').style.display = 'block';

  $('rec-leave-time').textContent = `LEAVE BY ${plan.recommendedDepartureTime}`;

  const statusBadge = $('rec-status-badge');
  statusBadge.textContent = plan.journeyStatus.replace(/_/g, ' ');
  statusBadge.className = 'exposure-badge ' + (
    plan.journeyStatus.includes('READY') ? 'lower' :
    plan.journeyStatus.includes('UPCOMING') ? 'moderate' :
    plan.journeyStatus.includes('MISSED') ? 'high' :
    plan.journeyStatus.includes('DISRUPTION') ? 'elevated' : 'uncertain'
  );

  $('rec-headline-sentence').textContent = plan.decisionSummary;

  // Equation strip
  $('eq-departure').textContent = plan.recommendedDepartureTime;
  $('eq-travel-time').textContent = `${Math.round(plan.travelDurationMinutes)} min`;
  $('eq-traffic-delay').textContent = plan.trafficDelayMinutes > 0
    ? `incl. ${plan.trafficDelayMinutes}m live delay`
    : 'free-flow conditions';
  $('eq-buffer').textContent = `${plan.safetyBufferMinutes} min`;
  $('eq-arrival').textContent = plan.targetArrivalTime;

  // Findings list
  const evidenceList = $('rec-evidence-list');
  evidenceList.innerHTML = (plan.evidenceFindings || []).map(f => `<li>${f}</li>`).join('');

  // Environmental breakdown
  const delayPercent = plan.travelDurationMinutes > 0
    ? Math.round((plan.trafficDelayMinutes / plan.travelDurationMinutes) * 100)
    : 0;
  $('rec-delay-ratio').textContent = `${delayPercent}% of travel time (${plan.trafficDelayMinutes} min delay)`;
  $('rec-incidents-count').textContent = `${plan.activeIncidentsCount} active disruptions`;
  $('rec-weather-factor').textContent = plan.weatherSummary ? plan.weatherSummary.split(',')[0] : 'Normal';

  const expBadge = $('rec-exposure-badge');
  expBadge.textContent = plan.exposureCategory.replace('_EXPOSURE', '');
  expBadge.className = 'exposure-badge-inline ' + (
    plan.exposureCategory.includes('HIGH') ? 'high' :
    plan.exposureCategory.includes('ELEVATED') ? 'elevated' :
    plan.exposureCategory.includes('MODERATE') ? 'moderate' : 'lower'
  );
}

// ============================================================================
// ROUTE ALTERNATIVES & MAP RENDERING
// ============================================================================

function renderRouteAlternatives(routes) {
  const container = $('route-comparison-cards');
  $('route-alternatives-container').style.display = 'block';

  container.innerHTML = routes.map((r, i) => {
    const summary = r.summary || {};
    const km = (summary.lengthInMeters / 1000).toFixed(1);
    const mins = Math.round(summary.travelTimeInSeconds / 60);
    const delayMins = summary.trafficDelayInSeconds ? Math.round(summary.trafficDelayInSeconds / 60) : 0;

    return `
      <div class="route-card-alt ${i === activeRouteIndex ? 'active' : ''}" data-index="${i}">
        <h4>Alternative ${i + 1} ${i === 0 ? '(Recommended)' : ''}</h4>
        <div class="route-stat"><span>Distance:</span> <strong>${km} km</strong></div>
        <div class="route-stat"><span>Travel Duration:</span> <strong>${mins} mins</strong></div>
        <div class="route-stat"><span>Traffic Delay:</span> <strong style="color: ${delayMins > 0 ? '#f97316' : '#4ade80'}">${delayMins > 0 ? `${delayMins} min delay` : 'Free-flow'}</strong></div>
      </div>
    `;
  }).join('');

  container.querySelectorAll('.route-card-alt').forEach(card => {
    card.addEventListener('click', () => {
      const idx = parseInt(card.getAttribute('data-index'), 10);
      container.querySelectorAll('.route-card-alt').forEach(c => c.classList.remove('active'));
      card.classList.add('active');
      renderRouteOnMap(idx);
    });
  });
}

function renderRouteOnMap(routeIndex) {
  if (!map || !map.isStyleLoaded() || !currentRoutes.length) return;
  const route = currentRoutes[routeIndex] || currentRoutes[0];
  activeRouteIndex = routeIndex;

  const coordinates = [];
  const legs = route.legs || [];
  legs.forEach(leg => {
    (leg.points || []).forEach(pt => coordinates.push([pt.longitude, pt.latitude]));
  });

  if (!coordinates.length) return;

  const geojson = {
    type: 'Feature',
    geometry: { type: 'LineString', coordinates: coordinates }
  };

  if (map.getSource('route-line')) {
    map.getSource('route-line').setData(geojson);
  } else {
    map.addSource('route-line', { type: 'geojson', data: geojson });
    map.addLayer({
      id: 'route-line-halo',
      type: 'line',
      source: 'route-line',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#064e3b', 'line-width': 8, 'line-opacity': 0.7 }
    });
    map.addLayer({
      id: 'route-line-core',
      type: 'line',
      source: 'route-line',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#4ade80', 'line-width': 4 }
    });
  }

  const bounds = coordinates.reduce((b, coord) => b.extend(coord), new maplibregl.LngLatBounds(coordinates[0], coordinates[0]));
  map.fitBounds(bounds, { padding: 45, maxZoom: 14 });
}

// ============================================================================
// SAVED COMMUTES & SPOTLIGHT
// ============================================================================

async function loadSavedCommutes() {
  try {
    const res = await fetch('/api/v1/commutes').then(r => r.json());
    if (res.status === 'LIVE' && Array.isArray(res.data)) {
      savedCommutesData = res.data;
      renderSavedCommutesList();
      loadNextUpcomingCommute();
    }
  } catch (err) {
    console.warn('Could not load saved commutes:', err);
  }
}

function renderSavedCommutesList() {
  const container = $('saved-commutes-list');
  if (savedCommutesData.length === 0) {
    container.innerHTML = '<div class="metric-empty">No saved commutes yet. Plan a commute above and click "Save as Recurring Commute" to monitor it daily.</div>';
    return;
  }

  container.innerHTML = savedCommutesData.map(c => {
    const statusText = c.enabled ? (c.journeyStatus ? c.journeyStatus.replace(/_/g, ' ') : 'ACTIVE') : 'PAUSED';
    const isPaused = !c.enabled;

    return `
      <div class="commute-card" data-id="${c.id}">
        <div class="commute-card-head">
          <div>
            <h4>${c.name}</h4>
            <span class="source-line">${c.weekdays} &middot; Target: <strong>${c.targetArrivalTime}</strong></span>
          </div>
          <span class="exposure-badge ${isPaused ? 'uncertain' : 'lower'}">${statusText}</span>
        </div>

        <div class="commute-card-body">
          <p class="muted"><strong>Route:</strong> ${c.originName} &rarr; ${c.destinationName}</p>
          <div class="commute-card-rec">
            <div>
              <small class="muted">LEAVE BY</small>
              <br><strong>${c.lastDepartureRecommendation || '--:--'}</strong>
            </div>
            <div>
              <small class="muted">DURATION</small>
              <br><span>${c.lastEstimatedDurationMinutes ? Math.round(c.lastEstimatedDurationMinutes) + ' min' : '--'}</span>
            </div>
            <div>
              <small class="muted">BUFFER</small>
              <br><span>${c.safetyBufferMinutes} min</span>
            </div>
          </div>
        </div>

        <div class="commute-card-actions">
          <button class="button button-small btn-recalc" data-id="${c.id}" type="button">🔄 Recalculate</button>
          <button class="button button-small button-outline btn-toggle" data-id="${c.id}" type="button">${c.enabled ? '⏸️ Pause' : '▶️ Resume'}</button>
          <button class="button button-small button-outline btn-feedback" data-id="${c.id}" type="button">📝 Feedback</button>
          <button class="button button-small button-outline btn-delete" data-id="${c.id}" style="color: #f87171;" type="button">🗑️</button>
        </div>
      </div>
    `;
  }).join('');

  // Wire buttons
  container.querySelectorAll('.btn-recalc').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.getAttribute('data-id');
      btn.textContent = 'Calculating…';
      try {
        const res = await fetch(`/api/v1/commutes/${id}/recalculate`, { method: 'POST' }).then(r => r.json());
        if (res.status === 'LIVE' && res.data) {
          renderDeparturePlanResult(res.data);
          loadSavedCommutes();
          alert(`Recalculated: Leave by ${res.data.recommendedDepartureTime} to reach destination on time.`);
        }
      } finally {
        btn.textContent = '🔄 Recalculate';
      }
    });
  });

  container.querySelectorAll('.btn-toggle').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.getAttribute('data-id');
      await fetch(`/api/v1/commutes/${id}/toggle`, { method: 'POST' });
      loadSavedCommutes();
    });
  });

  container.querySelectorAll('.btn-delete').forEach(btn => {
    btn.addEventListener('click', async () => {
      if (confirm('Delete this saved commute?')) {
        const id = btn.getAttribute('data-id');
        await fetch(`/api/v1/commutes/${id}`, { method: 'DELETE' });
        loadSavedCommutes();
      }
    });
  });

  container.querySelectorAll('.btn-feedback').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      openFeedbackModal(id);
    });
  });
}

async function loadNextUpcomingCommute() {
  try {
    const res = await fetch('/api/v1/commutes/next').then(r => r.json());
    const banner = $('next-commute-banner');
    if (res.status === 'LIVE' && res.data) {
      nextCommuteData = res.data;
      banner.style.display = 'block';

      $('next-commute-title').textContent = res.data.name;
      $('next-commute-departure').textContent = res.data.lastDepartureRecommendation || '--:--';
      $('next-commute-route-text').textContent = `From ${res.data.originName} → ${res.data.destinationName}`;
      $('next-commute-equation').innerHTML = `Target arrival: <strong>${res.data.targetArrivalTime}</strong> &middot; Travel: <strong>${res.data.lastEstimatedDurationMinutes ? Math.round(res.data.lastEstimatedDurationMinutes) : 30} min</strong> (incl. ${res.data.lastTrafficDelayMinutes || 0}m live delay) &middot; Safety buffer: <strong>${res.data.safetyBufferMinutes} min</strong>`;

      $('next-commute-badge').textContent = res.data.journeyStatus ? res.data.journeyStatus.replace(/_/g, ' ') : 'READY';

      checkCommuteAlert(res.data);
    } else {
      banner.style.display = 'none';
    }
  } catch {
    $('next-commute-banner').style.display = 'none';
  }
}

// ============================================================================
// COMMUTE NOTIFICATIONS & ALERTS
// ============================================================================

function setupAlerts() {
  const btn = $('alert-permission-btn');
  if (!btn) return;

  if (!('Notification' in window)) {
    btn.style.display = 'none';
    return;
  }

  if (Notification.permission === 'granted') {
    btn.textContent = '🔔 Alerts Active';
    btn.classList.remove('button-outline');
  }

  btn.addEventListener('click', async () => {
    if (Notification.permission === 'granted') {
      alert('Commute alerts are active! You will be notified when it is time to depart or if major delays occur.');
      return;
    }

    const permission = await Notification.requestPermission();
    if (permission === 'granted') {
      btn.textContent = '🔔 Alerts Active';
      btn.classList.remove('button-outline');
      new Notification('FlowCast Commute Alerts Activated', {
        body: 'You will receive timely departure alerts for your scheduled journeys.',
        icon: '/icon.svg'
      });
    } else {
      alert('Notification permission was denied. Alerts will show in-app only.');
    }
  });
}

function checkCommuteAlert(commute) {
  if (Notification.permission !== 'granted' || !commute || !commute.enabled) return;

  if (commute.journeyStatus === 'READY_TO_DEPART') {
    new Notification(`Commute Reminder: ${commute.name}`, {
      body: `Recommended departure is ${commute.lastDepartureRecommendation}. Leave now to reach your destination on time!`,
      icon: '/icon.svg'
    });
  }
}

// ============================================================================
// MODAL DIALOGS
// ============================================================================

function initModals() {
  // Save Commute Modal
  const saveModal = $('save-commute-modal');
  const openSaveBtn = $('save-current-commute-btn');
  const openSaveBtn2 = $('open-save-modal-btn');
  const closeSaveBtn = $('close-save-modal');
  const cancelSaveBtn = $('cancel-save-modal');
  const saveForm = $('save-commute-form');

  const showSaveModal = () => {
    if (selectedLocations.origin) $('save-origin-name').value = selectedLocations.origin.name || $('origin-query').value;
    if (selectedLocations.destination) $('save-dest-name').value = selectedLocations.destination.name || $('destination-query').value;
    $('save-arrival-time').value = $('target-arrival-time').value || '09:00';
    $('save-buffer').value = $('safety-buffer').value || '15';
    $('save-commute-name').value = selectedLocations.destination
      ? `Commute to ${selectedLocations.destination.name.split(',')[0]}`
      : 'Daily Commute';
    saveModal.style.display = 'flex';
  };

  const hideSaveModal = () => { saveModal.style.display = 'none'; };

  if (openSaveBtn) openSaveBtn.addEventListener('click', showSaveModal);
  if (openSaveBtn2) openSaveBtn2.addEventListener('click', showSaveModal);
  if (closeSaveBtn) closeSaveBtn.addEventListener('click', hideSaveModal);
  if (cancelSaveBtn) cancelSaveBtn.addEventListener('click', hideSaveModal);

  if (saveForm) {
    saveForm.addEventListener('submit', async e => {
      e.preventDefault();
      if (!selectedLocations.origin || !selectedLocations.destination) {
        alert('Please calculate a route above first to choose origin and destination.');
        return;
      }

      const payload = {
        name: $('save-commute-name').value.trim(),
        originName: $('save-origin-name').value.trim(),
        originLatitude: selectedLocations.origin.latitude,
        originLongitude: selectedLocations.origin.longitude,
        destinationName: $('save-dest-name').value.trim(),
        destinationLatitude: selectedLocations.destination.latitude,
        destinationLongitude: selectedLocations.destination.longitude,
        targetArrivalTime: $('save-arrival-time').value.trim(),
        weekdays: $('save-weekdays').value,
        safetyBufferMinutes: parseInt($('save-buffer').value, 10),
        enabled: true
      };

      try {
        const res = await fetch('/api/v1/commutes', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }).then(r => r.json());

        if (res.status === 'LIVE') {
          hideSaveModal();
          loadSavedCommutes();
          alert('Commute successfully saved! It is now monitored daily.');
        } else {
          alert('Could not save commute: ' + (res.reason || 'Unknown error'));
        }
      } catch {
        alert('Network error while saving commute.');
      }
    });
  }

  // Trip Feedback Modal
  const fbModal = $('feedback-modal');
  const closeFbBtn = $('close-feedback-modal');
  const cancelFbBtn = $('cancel-feedback-modal');
  const fbForm = $('feedback-form');

  const hideFbModal = () => { fbModal.style.display = 'none'; };
  if (closeFbBtn) closeFbBtn.addEventListener('click', hideFbModal);
  if (cancelFbBtn) cancelFbBtn.addEventListener('click', hideFbModal);

  if (fbForm) {
    fbForm.addEventListener('submit', async e => {
      e.preventDefault();
      const payload = {
        commuteId: parseInt($('feedback-commute-id').value, 10),
        wasOnTime: $('feedback-on-time').value === 'true',
        wasDepartureUseful: $('feedback-useful').value === 'true',
        actualDurationMinutes: $('feedback-duration').value ? parseInt($('feedback-duration').value, 10) : null,
        disruptionExperienced: $('feedback-disruption').value.trim() || 'None',
        feedbackNotes: 'Commuter voluntary trip completion record'
      };

      try {
        const res = await fetch('/api/v1/commutes/feedback', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }).then(r => r.json());

        if (res.status === 'LIVE') {
          hideFbModal();
          alert('Thank you! Your feedback helps calibrate future departure recommendations.');
        }
      } catch {
        alert('Network error while saving feedback.');
      }
    });
  }

  // Hazard Report Modal
  const repModal = $('report-modal');
  const openRepBtn = $('open-report-modal');
  const closeRepBtn = $('close-report-modal');
  const cancelRepBtn = $('cancel-report-btn');
  const repForm = $('report-form');
  const useRepLocBtn = $('report-use-location');

  const showRepModal = () => {
    if (searchCenter) {
      $('report-lat').value = searchCenter.latitude.toFixed(6);
      $('report-lon').value = searchCenter.longitude.toFixed(6);
    }
    repModal.style.display = 'flex';
  };
  const hideRepModal = () => { repModal.style.display = 'none'; };

  if (openRepBtn) openRepBtn.addEventListener('click', showRepModal);
  if (closeRepBtn) closeRepBtn.addEventListener('click', hideRepModal);
  if (cancelRepBtn) cancelRepBtn.addEventListener('click', hideRepModal);

  if (useRepLocBtn) {
    useRepLocBtn.addEventListener('click', () => {
      requestDeviceLocation(
        pos => {
          $('report-lat').value = pos.latitude.toFixed(6);
          $('report-lon').value = pos.longitude.toFixed(6);
        },
        err => alert(err)
      );
    });
  }

  if (repForm) {
    repForm.addEventListener('submit', async e => {
      e.preventDefault();
      const payload = {
        reportType: $('report-type').value,
        latitude: parseFloat($('report-lat').value),
        longitude: parseFloat($('report-lon').value),
        description: $('report-desc').value.trim()
      };

      try {
        const res = await fetch('/api/v1/reports', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }).then(r => r.json());

        if (res.status === 'LIVE') {
          hideRepModal();
          $('report-desc').value = '';
          loadCommunityReports();
          alert('Hazard report successfully recorded! It is now visible on the live map.');
        } else {
          alert('Submission error: ' + (res.reason || 'Could not submit report.'));
        }
      } catch {
        alert('Could not submit report. Please check your network connection.');
      }
    });
  }

  // Initialize touch gestures for bottom sheets
  initBottomSheetGestures();
}

function initBottomSheetGestures() {
  document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
    const sheet = backdrop.querySelector('.bottom-sheet');
    const handle = backdrop.querySelector('.bottom-sheet-handle');
    if (!sheet) return;

    // Close when tapping the dark backdrop outside content
    backdrop.addEventListener('click', e => {
      if (e.target === backdrop) {
        backdrop.style.display = 'none';
      }
    });

    // Native mobile swipe-down to dismiss
    let touchStartY = 0;
    let touchEndY = 0;
    let isDragging = false;

    const dragEl = handle || sheet;
    dragEl.addEventListener('touchstart', e => {
      touchStartY = e.touches[0].clientY;
      isDragging = sheet.scrollTop <= 2;
    }, { passive: true });

    dragEl.addEventListener('touchmove', e => {
      if (!isDragging) return;
      touchEndY = e.touches[0].clientY;
      const diff = touchEndY - touchStartY;
      if (diff > 0) {
        sheet.style.transform = `translateY(${diff * 0.75}px)`;
      }
    }, { passive: true });

    dragEl.addEventListener('touchend', () => {
      if (!isDragging) return;
      const diff = touchEndY - touchStartY;
      if (diff > 75) {
        backdrop.style.display = 'none';
      }
      sheet.style.transform = '';
      touchStartY = 0;
      touchEndY = 0;
      isDragging = false;
    });
  });
}

function initMobileBottomNav() {
  const navItems = document.querySelectorAll('.mobile-nav-item');
  if (!navItems.length) return;

  navItems.forEach(item => {
    item.addEventListener('click', e => {
      e.preventDefault();
      const targetId = item.getAttribute('data-target') || item.getAttribute('href')?.replace('#', '');
      const targetEl = document.getElementById(targetId);
      if (targetEl) {
        navItems.forEach(n => n.classList.remove('active'));
        item.classList.add('active');
        const headerOffset = 70;
        const elementPosition = targetEl.getBoundingClientRect().top;
        const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
        window.scrollTo({
          top: offsetPosition,
          behavior: 'smooth'
        });
      }
    });
  });

  // Track scroll position to update active bottom nav item (Scrollspy)
  const sections = [
    { id: 'commute-planner-section', nav: 'commute-planner-section' },
    { id: 'saved-commutes-section', nav: 'saved-commutes-section' },
    { id: 'map-section', nav: 'map-section' },
    { id: 'hazards-section', nav: 'hazards-section' },
    { id: 'sources-section', nav: 'sources-section' }
  ];

  let isTicking = false;
  window.addEventListener('scroll', () => {
    if (!isTicking) {
      window.requestAnimationFrame(() => {
        const scrollY = window.pageYOffset;
        let currentSectionId = sections[0].id;
        for (const sec of sections) {
          const el = document.getElementById(sec.id);
          if (el) {
            const top = el.offsetTop - 140;
            if (scrollY >= top) {
              currentSectionId = sec.id;
            }
          }
        }
        navItems.forEach(item => {
          if (item.getAttribute('data-target') === currentSectionId) {
            item.classList.add('active');
          } else {
            item.classList.remove('active');
          }
        });
        isTicking = false;
      });
      isTicking = true;
    }
  }, { passive: true });
}

function openFeedbackModal(commuteId) {
  $('feedback-commute-id').value = commuteId;
  $('feedback-modal').style.display = 'flex';
}

// ============================================================================
// MAP LAYERS & MARKERS
// ============================================================================

function renderIncidents() {
  if (!map) return;
  incidentMarkers.forEach(m => m.remove());
  incidentMarkers = [];

  const toggle = $('incidents-toggle');
  if (toggle && !toggle.checked) return;

  incidentData.forEach(item => {
    const coords = item.geometry?.[0];
    if (!Array.isArray(coords) || coords.length < 2) return;

    const delayText = item.delaySeconds ? ` · ${Math.round(item.delaySeconds / 60)} min delay` : '';
    const popupContent = `
      <div style="color: #0f172a; font-family: sans-serif; font-size: 0.85rem;">
        <strong style="color: #ea580c;">${item.category || 'Incident'}: ${item.description || 'Traffic disruption'}</strong><br>
        <span style="color: #475569;">${item.roadName || 'Road not specified'}</span><br>
        <small style="color: #64748b;">Severity: ${item.severity || 'Reported'}${delayText}</small>
      </div>`;

    const marker = new maplibregl.Marker({ color: '#ea580c' })
      .setLngLat(coords)
      .setPopup(new maplibregl.Popup({ offset: 15 }).setHTML(popupContent))
      .addTo(map);

    incidentMarkers.push(marker);
  });
}

function renderCommunityReports() {
  if (!map) return;
  reportMarkers.forEach(m => m.remove());
  reportMarkers = [];

  const toggle = $('reports-toggle');
  if (toggle && !toggle.checked) return;

  communityReportsData.forEach(rep => {
    if (!Number.isFinite(rep.latitude) || !Number.isFinite(rep.longitude)) return;

    const popupContent = `
      <div style="color: #0f172a; font-family: sans-serif; font-size: 0.85rem;">
        <strong style="color: #ca8a04;">⚠️ ${rep.reportType}</strong><br>
        <p style="margin: 0.25rem 0;">${rep.description}</p>
        <small style="color: #64748b;">Status: ${rep.status} · Community Report</small>
      </div>`;

    const marker = new maplibregl.Marker({ color: '#eab308' })
      .setLngLat([rep.longitude, rep.latitude])
      .setPopup(new maplibregl.Popup({ offset: 15 }).setHTML(popupContent))
      .addTo(map);

    reportMarkers.push(marker);
  });
}

// ============================================================================
// SUPPORTING DATA LOADERS
// ============================================================================

async function loadWeatherForecast() {
  try {
    const res = await fetch('/api/v1/weather/forecast').then(r => r.json());
    if (res.status === 'LIVE' && res.data?.hourly) {
      const container = $('weather-forecast-preview');
      const items = res.data.hourly.slice(0, 8);
      container.innerHTML = items.map(it => {
        const hour = it.time.split('T')[1]?.substring(0, 5) || it.time;
        return `
          <div class="weather-pill">
            <small>${hour}</small>
            <strong>${it.temperatureCelsius.toFixed(0)}°C</strong>
            <small style="color: ${it.precipitationMm > 0 ? '#38bdf8' : '#8ea1bb'}">${it.precipitationMm > 0 ? `${it.precipitationMm}mm` : `${it.windSpeedKmh.toFixed(0)}km/h`}</small>
          </div>
        `;
      }).join('');
    }
  } catch (err) {
    console.warn('Weather forecast load error:', err);
  }
}

async function loadForecastReadiness() {
  try {
    const res = await fetch('/api/v1/forecasts/readiness').then(r => r.json());
    if (res.status === 'LIVE' && res.data) {
      const data = res.data;
      const statusBadge = $('forecast-status');
      statusBadge.textContent = data.status;
      statusBadge.className = 'status ' + (data.isReady ? 'ready' : 'unavailable');

      const percent = Math.min(100, Math.round((data.observationsCount / data.requiredObservationsThreshold) * 100));
      $('observations-bar').style.width = `${percent}%`;
      $('obs-count-text').textContent = `${data.observationsCount} / ${data.requiredObservationsThreshold} genuine observations`;
      $('obs-percent-text').textContent = `${percent}%`;

      const list = $('readiness-criteria');
      list.innerHTML = (data.criteria || []).map(c => `<li>${c}</li>`).join('');
    }
  } catch (err) {
    console.warn('Forecast readiness error:', err);
  }
}

async function loadCommunityReports() {
  try {
    const res = await fetch('/api/v1/reports').then(r => r.json());
    if (res.status === 'LIVE' && res.data) {
      communityReportsData = res.data;
      renderCommunityReports();

      const container = $('community-reports-list');
      if (communityReportsData.length === 0) {
        container.innerHTML = '<div class="metric-empty">No community hazards submitted yet. Commuter submissions are currently empty.</div>';
        return;
      }

      container.innerHTML = communityReportsData.slice(0, 6).map(rep => {
        const time = new Date(rep.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        return `
          <div class="report-item">
            <div class="report-item-head">
              <span class="report-type-pill">${rep.reportType}</span>
              <small class="muted">${time}</small>
            </div>
            <p style="margin: 0.35rem 0; font-size: 0.85rem; color: #f1f5f9;">${rep.description}</p>
            <small class="source-line">Status: <span style="color: #65e0b7;">${rep.status}</span> · Lat: ${rep.latitude.toFixed(4)}, Lon: ${rep.longitude.toFixed(4)}</small>
          </div>
        `;
      }).join('');
    }
  } catch (err) {
    console.warn('Community reports error:', err);
  }
}

async function loadSourceHealth() {
  try {
    const res = await fetch('/api/v1/sources').then(r => r.json());
    if (res.status === 'LIVE' && res.data) {
      const container = $('sources');
      container.innerHTML = res.data.map(src => {
        const isOk = src.status === 'READY';
        return `
          <div class="source-box">
            <div>
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem;">
                <h4 style="margin: 0;">${src.name}</h4>
                <span class="status ${isOk ? 'ready' : 'unavailable'}">${src.status}</span>
              </div>
              <p class="source-line" style="margin-bottom: 0.35rem;">Region: ${src.coverageRegion}</p>
              <ul>${(src.capabilities || []).map(c => `<li>${c}</li>`).join('')}</ul>
            </div>
            <div style="margin-top: 0.65rem; border-top: 1px solid #1e293b; padding-top: 0.45rem;">
              <small class="muted">${src.setupInstructions || ''}</small>
            </div>
          </div>
        `;
      }).join('');
    }
  } catch (err) {
    console.warn('Sources error:', err);
  }
}

// ============================================================================
// MAIN APPLICATION INITIALIZATION
// ============================================================================

async function init() {
  $('network-status').textContent = 'Connecting sources…';
  updateOfflineStatus();
  initLandmarkChips();
  initModals();
  initMobileBottomNav();
  setupAlerts();

  setupLocationSearch('origin');
  setupLocationSearch('destination');

  // Dismiss suggestions when tapping outside anywhere
  document.addEventListener('click', e => {
    if (!e.target.closest('#origin-query') && !e.target.closest('#origin-suggestions')) {
      const el = $('origin-suggestions');
      if (el) el.innerHTML = '';
    }
    if (!e.target.closest('#destination-query') && !e.target.closest('#destination-suggestions')) {
      const el = $('destination-suggestions');
      if (el) el.innerHTML = '';
    }
  });

  // Origin "Use my location"
  const useOriginBtn = $('use-origin-btn');
  if (useOriginBtn) {
    useOriginBtn.addEventListener('click', () => {
      requestDeviceLocation(
        pos => {
          selectedLocations.origin = {
            name: 'Current Location',
            latitude: pos.latitude,
            longitude: pos.longitude
          };
          $('origin-query').value = 'Current Location';
          $('origin-suggestions').innerHTML = '';
        },
        err => alert(err)
      );
    });
  }

  // Hook commute form
  const commuteForm = $('commute-form');
  if (commuteForm) commuteForm.addEventListener('submit', calculateCommuteDeparture);

  // Hook next commute recalculate
  const nextRecalcBtn = $('next-commute-recalculate-btn');
  if (nextRecalcBtn) {
    nextRecalcBtn.addEventListener('click', async () => {
      if (nextCommuteData?.id) {
        nextRecalcBtn.textContent = 'Calculating…';
        try {
          const res = await fetch(`/api/v1/commutes/${nextCommuteData.id}/recalculate`, { method: 'POST' }).then(r => r.json());
          if (res.status === 'LIVE' && res.data) {
            renderDeparturePlanResult(res.data);
            loadNextUpcomingCommute();
          }
        } finally {
          nextRecalcBtn.textContent = '🔄 Recalculate Live';
        }
      }
    });
  }

  const nextFbBtn = $('next-commute-feedback-btn');
  if (nextFbBtn) {
    nextFbBtn.addEventListener('click', () => {
      if (nextCommuteData?.id) openFeedbackModal(nextCommuteData.id);
    });
  }

  // Layer toggles
  $('traffic-toggle')?.addEventListener('change', e => {
    if (map && map.getLayer('tomtom-traffic')) {
      map.setLayoutProperty('tomtom-traffic', 'visibility', e.target.checked ? 'visible' : 'none');
    }
  });
  $('incidents-toggle')?.addEventListener('change', renderIncidents);
  $('reports-toggle')?.addEventListener('change', renderCommunityReports);

  // Refresh button
  $('refresh-sources-btn')?.addEventListener('click', loadSourceHealth);

  try {
    const [config, weather] = await Promise.all([
      fetch('/api/v1/map/config').then(r => r.json()),
      fetch('/api/v1/weather/current').then(r => r.json())
    ]);

    if (config.status === 'LIVE' && config.data) {
      searchCenter = { latitude: config.data.latitude, longitude: config.data.longitude };
      $('map-status').textContent = config.data.baseTileTemplate ? 'Loading map tiles…' : 'Map key is not configured.';

      const delta = 0.15;
      const incRes = await fetch(`/api/v1/traffic/incidents?west=${config.data.longitude - delta}&south=${config.data.latitude - delta}&east=${config.data.longitude + delta}&north=${config.data.latitude + delta}`)
        .then(r => r.json());

      incidentData = incRes.data || [];

      // Initialize MapLibre
      if (config.data.baseTileTemplate) {
        map = new maplibregl.Map({
          container: 'map',
          style: {
            version: 8,
            sources: {
              'tomtom-base': {
                type: 'raster',
                tiles: [config.data.baseTileTemplate],
                tileSize: 256,
                attribution: '&copy; TomTom Maps'
              },
              'tomtom-traffic': {
                type: 'raster',
                tiles: [config.data.trafficTileTemplate],
                tileSize: 256
              }
            },
            layers: [
              { id: 'tomtom-base', type: 'raster', source: 'tomtom-base', minzoom: 0, maxzoom: 22 },
              { id: 'tomtom-traffic', type: 'raster', source: 'tomtom-traffic', minzoom: 0, maxzoom: 22 }
            ]
          },
          center: [config.data.longitude, config.data.latitude],
          zoom: 12
        });

        map.addControl(new maplibregl.NavigationControl(), 'top-right');
        map.on('load', () => {
          renderIncidents();
          renderCommunityReports();
        });
      }
    }

    if (weather.status === 'LIVE' && weather.data) {
      const d = weather.data;
      $('weather').innerHTML = `
        <div class="metric-pair"><span>Temperature:</span> <strong>${d.temperatureCelsius.toFixed(1)}°C</strong></div>
        <div class="metric-pair"><span>Relative Humidity:</span> <strong>${d.relativeHumidityPercent.toFixed(0)}%</strong></div>
        <div class="metric-pair"><span>Precipitation:</span> <strong>${d.precipitationMillimetres.toFixed(1)} mm</strong></div>
        <div class="metric-pair"><span>Wind Speed:</span> <strong>${d.windSpeedKmh.toFixed(1)} km/h</strong></div>
      `;
      setStatus($('weather-status'), 'READY');
    }

    $('network-status').textContent = 'Live System Connected';
    $('network-status').className = 'pill ready';
  } catch (err) {
    console.error('Initialization error:', err);
    $('network-status').textContent = 'Connection Error';
    $('network-status').className = 'pill unavailable';
  }

  // Load secondary modules
  loadWeatherForecast();
  loadSavedCommutes();
  loadCommunityReports();
  loadSourceHealth();
  loadForecastReadiness();

  // Register PWA service worker
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(e => console.warn('SW registration:', e));
  }
}

window.addEventListener('DOMContentLoaded', init);
