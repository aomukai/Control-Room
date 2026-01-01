// Modal Infrastructure
// Provides showModal() and createModalShell() for building modals
(function() {
    'use strict';

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Show a simple input modal with OK/Cancel buttons.
     * @param {string} title - Modal title
     * @param {string} placeholder - Input placeholder text
     * @param {Function} callback - Called with input value on confirm
     * @param {string} hint - Optional hint text below input
     */
    function showModal(title, placeholder, callback, hint = '') {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal';

        const hintHtml = hint ? `<div class="modal-hint">${escapeHtml(hint)}</div>` : '';

        modal.innerHTML = `
            <div class="modal-title">${escapeHtml(title)}</div>
            <input type="text" class="modal-input" placeholder="${escapeHtml(placeholder)}">
            ${hintHtml}
            <div class="modal-buttons">
                <button class="modal-btn modal-btn-secondary" data-action="cancel">Cancel</button>
                <button class="modal-btn modal-btn-primary" data-action="confirm">OK</button>
            </div>
        `;

        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        const input = modal.querySelector('.modal-input');
        input.focus();

        const close = () => overlay.remove();

        modal.querySelector('[data-action="cancel"]').addEventListener('click', close);
        modal.querySelector('[data-action="confirm"]').addEventListener('click', () => {
            callback(input.value);
            close();
        });

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                callback(input.value);
                close();
            } else if (e.key === 'Escape') {
                close();
            }
        });

        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close();
        });
    }

    /**
     * Create a modal shell with customizable buttons.
     * Returns DOM elements for further customization.
     * @param {string} title - Modal title
     * @param {string} confirmLabel - Confirm button text
     * @param {string} cancelLabel - Cancel button text
     * @param {Object} options - Additional options
     * @returns {Object} { overlay, modal, body, confirmBtn, cancelBtn, close }
     */
    function createModalShell(title, confirmLabel = 'OK', cancelLabel = 'Cancel', options = {}) {
        const {
            closeOnCancel = true,
            closeOnConfirm = false,
            confirmTitle = '',
            cancelTitle = '',
            onClose = null
        } = options;

        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';

        const modal = document.createElement('div');
        modal.className = 'modal';

        const header = document.createElement('div');
        header.className = 'modal-title';
        header.textContent = title;

        const body = document.createElement('div');
        body.className = 'modal-body';

        const buttons = document.createElement('div');
        buttons.className = 'modal-buttons';

        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'modal-btn modal-btn-secondary';
        cancelBtn.type = 'button';
        cancelBtn.textContent = cancelLabel;
        if (cancelTitle) {
            cancelBtn.title = cancelTitle;
            cancelBtn.setAttribute('aria-label', cancelTitle);
        }

        const confirmBtn = document.createElement('button');
        confirmBtn.className = 'modal-btn modal-btn-primary';
        confirmBtn.type = 'button';
        confirmBtn.textContent = confirmLabel;
        if (confirmTitle) {
            confirmBtn.title = confirmTitle;
            confirmBtn.setAttribute('aria-label', confirmTitle);
        }

        buttons.appendChild(cancelBtn);
        buttons.appendChild(confirmBtn);

        modal.appendChild(header);
        modal.appendChild(body);
        modal.appendChild(buttons);
        overlay.appendChild(modal);
        document.body.appendChild(overlay);

        let isClosed = false;
        const close = () => {
            if (isClosed) return;
            isClosed = true;
            overlay.remove();
            if (typeof onClose === 'function') {
                onClose();
            }
        };

        if (closeOnCancel) {
            cancelBtn.addEventListener('click', close);
        }
        if (closeOnConfirm) {
            confirmBtn.addEventListener('click', close);
        }
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) close();
        });

        const handleKeydown = (e) => {
            if (e.key === 'Escape') {
                close();
                document.removeEventListener('keydown', handleKeydown);
            }
        };
        document.addEventListener('keydown', handleKeydown);

        return { overlay, modal, body, confirmBtn, cancelBtn, close };
    }

    // Expose to window
    window.modals = {
        escapeHtml,
        showModal,
        createModalShell
    };
})();
