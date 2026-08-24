(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    }
    root.OrderConsole = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    const MAX_POLLS = 6;
    const POLL_INTERVAL_MS = 1000;

    function buildOrderRequest(values) {
        return {
            orderNo: values.orderNo.trim(),
            sku: values.sku,
            quantity: Number(values.quantity),
            note: values.note.trim()
        };
    }

    function initialize() {
        const form = document.getElementById('order-form');
        if (!form) {
            return;
        }
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            submitOrder(form);
        });
    }

    async function submitOrder(form) {
        const button = document.getElementById('submit-order');
        const request = buildOrderRequest(Object.fromEntries(new FormData(form)));
        button.disabled = true;
        showStatus('pending', '正在提交', request, '订单请求已发送，正在等待履约结果。');
        try {
            const response = await fetch('/sample/orders', requestOptions('POST', request));
            if (!response.ok) {
                throw new Error('订单服务返回 ' + response.status);
            }
            await pollStatus(request.orderNo, request);
        } catch (error) {
            showStatus('error', '履约失败', request, error.message);
        } finally {
            button.disabled = false;
        }
    }

    async function pollStatus(orderNo, request) {
        for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
            const response = await fetch('/sample/orders/' + encodeURIComponent(orderNo));
            if (response.ok) {
                const status = await response.json();
                showStatus('success', status.status || '已履约', status, '订单已完成履约。');
                return;
            }
            if (response.status !== 404) {
                throw new Error('状态查询返回 ' + response.status);
            }
            await delay(POLL_INTERVAL_MS);
        }
        showStatus('pending', '等待确认', request, '订单已提交，暂未获得履约记录。');
    }

    function requestOptions(method, body) {
        return { method: method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
    }

    function showStatus(state, title, order, message) {
        const badge = document.getElementById('status-badge');
        badge.dataset.state = state;
        badge.textContent = title;
        setText('status-order-no', order.orderNo || '--');
        setText('status-sku', order.sku || '--');
        setText('status-quantity', order.quantity == null ? '--' : String(order.quantity));
        setText('status-time', order.fulfilledAt ? new Date(order.fulfilledAt).toLocaleString('zh-CN') : '--');
        setText('status-message', message);
    }

    function setText(id, value) {
        document.getElementById(id).textContent = value;
    }

    function delay(milliseconds) {
        return new Promise(function (resolve) { setTimeout(resolve, milliseconds); });
    }

    if (typeof document !== 'undefined') {
        document.addEventListener('DOMContentLoaded', initialize);
    }

    return { buildOrderRequest: buildOrderRequest };
}));
