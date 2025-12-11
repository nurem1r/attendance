/**
 * teacher-dashboard.js — student_status временно отключён:
 * - ФИО не кликаются, drawer не используется
 * - Остальной функционал сохранить/mark/update остался
 */
(function(){
    const apiJsonUrl = '/teacher/attendance/json';
    const saveUrl = '/teacher/attendance/save_batch';
    const dateInput = document.getElementById('datePicker');
    const minDateMeta = document.querySelector('meta[name="min-date"]');
    const studentsTableBody = document.querySelector('#studentsTable tbody');
    const saveBtn = document.getElementById('saveChangesBtn');

    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : null;
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    const minDate = (minDateMeta && minDateMeta.getAttribute('content')) ? minDateMeta.getAttribute('content') : '2025-12-01';

    let currentDate = null;
    const changes = new Map();

    // init flatpickr
    const fp = flatpickr(dateInput, {
        dateFormat: 'Y-m-d',
        defaultDate: (new Date()).toISOString().slice(0,10) < minDate ? minDate : (new Date()).toISOString().slice(0,10),
        minDate: minDate,
        onChange: function(selectedDates, dateStr) { loadForDate(dateStr); }
    });

    // thresholds
    const WARN_THRESHOLD = 4; // show warning if remaining < WARN_THRESHOLD
    const CRITICAL_THRESHOLD = 2;

    function createRow(student) {
        const tr = document.createElement('tr');
        tr.dataset.studentId = student.id;
        tr.dataset.remaining = (student.remainingLessons == null ? '' : student.remainingLessons);
        tr.dataset.baseRemaining = tr.dataset.remaining;
        tr.classList.remove('row-modified');

        // name cell — non-clickable while student_status is frozen
        const tdName = document.createElement('td');
        tdName.innerHTML = `<div style="font-weight:600">${escapeHtml(student.lastName)} ${escapeHtml(student.firstName)}</div>
                        <div class="kv" style="color:#666">${escapeHtml(student.studentCode || '')}</div>`;
        tr.appendChild(tdName);

        // package
        const tdPkg = document.createElement('td');
        tdPkg.textContent = student.packageType || (student.lessonPackageTitle || '-');
        tr.appendChild(tdPkg);

        // book
        const tdBook = document.createElement('td');
        tdBook.textContent = (student.needsBook == true ? 'Да' : 'Нет');
        tr.appendChild(tdBook);

        // remaining — explicit column
        const tdRemaining = document.createElement('td');
        tdRemaining.className = 'remaining-cell';
        tdRemaining.textContent = (student.remainingLessons == null ? '-' : student.remainingLessons);
        tr.appendChild(tdRemaining);

        // debt column
        const tdDebt = document.createElement('td');
        tdDebt.className = 'debt-cell';
        tdDebt.textContent = (student.debt == null ? '-' : student.debt);
        tr.appendChild(tdDebt);

        // status
        const tdStatus = document.createElement('td');
        tdStatus.className = 'status-cell';
        const att = student.attendance;
        tdStatus.textContent = att && att.status ? humanStatus(att.status) : 'Не отмечен';
        tr.appendChild(tdStatus);

        // actions
        const tdActions = document.createElement('td');
        tdActions.style.width = '220px';
        tdActions.style.whiteSpace = 'nowrap';
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'actions';

        const btnPresent = makeActionBtn('present', 'action-present', 'fa-check', 'PRESENT');
        const btnLate = makeActionBtn('late', 'action-late', 'fa-stopwatch', 'LATE');
        const btnAbsent = makeActionBtn('absent', 'action-absent', 'fa-xmark', 'ABSENT');
        const btnExcused = makeActionBtn('excused', 'action-excused', 'fa-user-shield', 'EXCUSED');

        [btnPresent, btnLate, btnAbsent, btnExcused].forEach(b => {
            b.addEventListener('click', (ev) => {
                ev.stopPropagation();
                const sid = tr.dataset.studentId;
                const prev = changes.get(sid) || { status: null, extra: 0 };
                prev.status = b.dataset.status;
                changes.set(sid, prev);
                tdStatus.textContent = humanStatus(prev.status);
                markRowModified(tr);
                updateRemainingDisplay(tr);
                updateRowState(tr);
            });
        });

        actionsDiv.appendChild(btnPresent);
        actionsDiv.appendChild(btnLate);
        actionsDiv.appendChild(btnExcused);
        actionsDiv.appendChild(btnAbsent);

        // extra control
        const extraWrapper = document.createElement('div');
        extraWrapper.className = 'extra-ctrl';
        const compact = document.createElement('button');
        compact.type = 'button';
        compact.className = 'extra-compact';
        compact.textContent = 'Доп. Урок';
        extraWrapper.appendChild(compact);

        const panel = document.createElement('div');
        panel.className = 'extra-panel';
        panel.style.display = 'none';
        const dec = document.createElement('button'); dec.type = 'button'; dec.textContent = '−';
        const deltaSpan = document.createElement('span'); deltaSpan.className = 'delta'; deltaSpan.textContent = '0';
        const inc = document.createElement('button'); inc.type = 'button'; inc.textContent = '+';
        panel.appendChild(dec); panel.appendChild(deltaSpan); panel.appendChild(inc);
        extraWrapper.appendChild(panel);

        compact.addEventListener('click', () => { panel.style.display = (panel.style.display === 'none') ? 'flex' : 'none'; });

        inc.addEventListener('click', (ev) => {
            ev.stopPropagation();
            const sid = tr.dataset.studentId;
            const obj = changes.get(sid) || { status: null, extra: 0 };
            obj.extra = (obj.extra || 0) + 1;
            deltaSpan.textContent = obj.extra;
            changes.set(sid, obj);
            markRowModified(tr);
            updateRemainingDisplay(tr);
            updateRowState(tr);
        });
        dec.addEventListener('click', (ev) => {
            ev.stopPropagation();
            const sid = tr.dataset.studentId;
            const obj = changes.get(sid) || { status: null, extra: 0 };
            obj.extra = (obj.extra || 0) - 1;
            deltaSpan.textContent = obj.extra;
            changes.set(sid, obj);
            markRowModified(tr);
            updateRemainingDisplay(tr);
            updateRowState(tr);
        });

        const existing = changes.get(String(student.id));
        if (existing && existing.extra) {
            deltaSpan.textContent = existing.extra;
            panel.style.display = 'flex';
        }

        tdActions.appendChild(actionsDiv);
        tdActions.appendChild(extraWrapper);
        tr.appendChild(tdActions);

        // store debt for state updates
        tr.__studentDebt = student.debt || 0;

        // initial row coloring
        updateRowState(tr);

        return tr;
    }

    // action button factory
    function makeActionBtn(name, cls, iconClass, status) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = `action-btn ${cls}`;
        b.dataset.status = status;
        b.title = name;
        b.innerHTML = `<i class="fa ${iconClass}"></i>`;
        b.addEventListener('click', (e)=> e.stopPropagation());
        return b;
    }

    function humanStatus(code) {
        switch(code) {
            case 'PRESENT': return 'Присутствует';
            case 'LATE': return 'Опоздал';
            case 'ABSENT': return 'Отсутствует';
            case 'EXCUSED': return 'Освобождён';
            default: return 'Не отмечен';
        }
    }

    function markRowModified(tr) { tr.classList.add('row-modified'); }
    function unmarkRowModified(tr) { tr.classList.remove('row-modified'); }

    function updateRemainingDisplay(tr) {
        const sid = tr.dataset.studentId;
        const baseRaw = tr.dataset.baseRemaining;
        const base = (baseRaw === '' || baseRaw === undefined) ? null : parseInt(baseRaw,10);
        const change = changes.get(sid);
        const delta = change ? (change.extra || 0) : 0;
        const remCell = tr.querySelector('.remaining-cell');
        if (base === null) { remCell.textContent = '-'; return; }
        const resulting = base - delta;
        remCell.textContent = resulting;
        let warn = tr.querySelector('.negative-warning');
        if (!warn) {
            warn = document.createElement('span');
            warn.className = 'negative-warning';
            warn.textContent = ' Внимание: остаток < 0';
            tr.querySelector('td.status-cell').appendChild(warn);
        }
        warn.style.display = (resulting < 0) ? 'inline' : 'none';
    }

    function updateRowState(tr) {
        const sid = tr.dataset.studentId;
        const debt = Number(tr.__studentDebt || 0);
        const baseRaw = tr.dataset.baseRemaining;
        const base = (baseRaw === '' || baseRaw === undefined) ? null : parseInt(baseRaw,10);
        const change = changes.get(sid);
        const delta = change ? (change.extra || 0) : 0;
        const resulting = base === null ? null : base - delta;

        tr.classList.remove('row-debt','row-low','row-warn');

        if (debt && debt > 0) {
            tr.classList.add('row-debt');
            return;
        }
        if (resulting !== null) {
            if (resulting < CRITICAL_THRESHOLD) {
                tr.classList.add('row-low');
            } else if (resulting < WARN_THRESHOLD) {
                tr.classList.add('row-warn');
            }
        }
    }

    async function loadForDate(dateStr) {
        try {
            if (!dateStr) dateStr = fp.input.value;
            if (dateStr < minDate) {
                alert('Выбранная дата раньше допустимой (' + minDate + '). Установлена минимальная дата.');
                fp.setDate(minDate);
                dateStr = minDate;
            }
            if (changes.size > 0 && dateStr !== currentDate) {
                if (!confirm('Есть несохранённые изменения — при переходе на другую дату они будут потеряны. Продолжить?')) {
                    fp.setDate(currentDate);
                    return;
                }
                changes.clear();
            }
            currentDate = dateStr;
            const res = await fetch(`${apiJsonUrl}?date=${encodeURIComponent(dateStr)}`, { credentials:'same-origin' });
            if (!res.ok) {
                let body = null;
                try { body = await res.json(); } catch(e) { body = await res.text(); }
                if (body && body.error === 'date_too_early') {
                    alert('Ошибка: выбранная дата раньше минимальной: ' + (body.minDate || minDate));
                    fp.setDate(body.minDate || minDate);
                    return;
                } else {
                    throw new Error((body && body.error) ? body.error : 'Ошибка загрузки');
                }
            }
            const arr = await res.json();
            studentsTableBody.innerHTML = '';
            arr.forEach(s => {
                const r = createRow(s);
                r.__studentDebt = s.debt || 0;
                studentsTableBody.appendChild(r);
                updateRowState(r);
            });
        } catch (err) {
            alert('Не удалось загрузить список: ' + err.message);
            console.error(err);
        }
    }

    async function saveBatch() {
        if (changes.size === 0) { alert('Нет изменений для сохранения'); return; }
        const items = [];
        for (const [sid, obj] of changes.entries()) {
            items.push({ studentId: parseInt(sid,10), status: obj.status || null, extraLessons: obj.extra || 0 });
        }
        const payload = { date: currentDate, items };
        const anyNegative = Array.from(document.querySelectorAll('td.remaining-cell')).some(td => {
            const val = parseInt(td.textContent,10);
            return !isNaN(val) && val < 0;
        });
        if (anyNegative) {
            if (!confirm('В некоторых строках остаток стал отрицательным. Сохранить?')) return;
        }
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken) headers[csrfHeader] = csrfToken;
            const r = await fetch(saveUrl, { method: 'POST', credentials: 'same-origin', headers, body: JSON.stringify(payload) });
            if (!r.ok) {
                const text = await r.text();
                throw new Error(text || 'Server error');
            }
            const json = await r.json();
            if (json.success) {
                alert('Сохранено успешно');
                changes.clear();
                await loadForDate(currentDate);
            } else {
                alert('Ошибка сохранения: ' + (json.error || JSON.stringify(json)));
            }
        } catch (err) {
            alert('Ошибка при сохранении: ' + err.message);
            console.error(err);
        }
    }

    function escapeHtml(s) { if (!s) return ''; return s.replace(/[&<>"']/g, function(m){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":"&#39;"})[m]; }); }

    saveBtn.addEventListener('click', saveBatch);

    // initial load
    loadForDate(fp.input.value);
})();