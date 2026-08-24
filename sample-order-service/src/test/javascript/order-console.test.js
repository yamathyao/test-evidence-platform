const assert = require('node:assert/strict');
const test = require('node:test');
const { buildOrderRequest } = require('../../main/resources/static/order-console.js');

test('buildOrderRequest converts the form quantity to a number', () => {
    assert.deepEqual(
        buildOrderRequest({ orderNo: 'BROWSER-001', sku: 'SKU-COFFEE', quantity: '2', note: '' }),
        { orderNo: 'BROWSER-001', sku: 'SKU-COFFEE', quantity: 2, note: '' }
    );
});

test('buildOrderRequest preserves the browser correlation order number', () => {
    const request = buildOrderRequest({ orderNo: 'RUN-20260819-001', sku: 'SKU-TEA', quantity: '1', note: 'review' });

    assert.equal(request.orderNo, 'RUN-20260819-001');
});
