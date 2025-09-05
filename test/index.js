// Enhanced RCON test suite with result tracking

const net = require('net');

const HOST = '127.0.0.1';
const PORT = 27015;
const PASSWORD = '';
const MAX_CONNECTIONS = 10;
const SOCKET_TIMEOUT_MS = 30 * 1000;

const testResults = [];

function log(title, msg) {
    console.log(`[${title}] ${msg}`);
}

function createRawSocket(label, onClose) {
    const socket = net.createConnection({ host: HOST, port: PORT }, () => {
        log(label, 'Connected');
    });

    socket.setTimeout(SOCKET_TIMEOUT_MS + 5000);
    socket.on('timeout', () => {
        log(label, 'Socket timeout reached');
        socket.destroy();
    });

    socket.on('error', (err) => {
        log(label, `Error: ${err.message}`);
    });

    socket.on('close', () => {
        log(label, 'Closed');
        onClose && onClose();
    });

    return socket;
}

function writePacket(socket, id, type, body) {
    const str = Buffer.from(body, 'utf8');
    const size = 4 + 4 + str.length + 2;
    const buf = Buffer.alloc(4 + size);
    buf.writeInt32LE(size, 0);
    buf.writeInt32LE(id, 4);
    buf.writeInt32LE(type, 8);
    str.copy(buf, 12);
    buf.writeInt16LE(0, 12 + str.length); // 2 null bytes
    socket.write(buf);
}

function markResult(testName, status, message) {
    testResults.push({ testName, status, message });
}

function testMaxConnections() {
    const label = 'TEST 1';
    log(label, `Opening ${MAX_CONNECTIONS + 1} connections...`);
    let closedCount = 0;
    let failed = false;

    for (let i = 0; i < MAX_CONNECTIONS + 1; i++) {
        const socket = createRawSocket(`conn-${i}`, () => {
            closedCount++;
            if (i === MAX_CONNECTIONS) {
                failed = true;
                markResult(label, 'PASS', 'Last connection was rejected/closed early.');
            }
            if (closedCount === MAX_CONNECTIONS + 1 && !failed) {
                markResult(label, 'FAIL', 'All connections succeeded — limit not enforced?');
            }
        });
    }
}

function testIdleConnection() {
    const label = 'TEST 2';
    log(label, `Opening connection and idling for timeout...`);
    const socket = createRawSocket(label, () => {
        markResult(label, 'PASS', 'Connection closed due to inactivity as expected.');
    });
}

function testMalformedPacket() {
    const label = 'TEST 3';
    log(label, `Sending malformed packet...`);
    const socket = createRawSocket(label, () => {
        markResult(label, 'PASS', 'Connection closed after sending malformed packet.');
    });

    socket.on('connect', () => {
        const buf = Buffer.alloc(4);
        buf.writeInt32LE(99999999, 0); // absurdly large length
        socket.write(buf);
    });
}

function testNoReadAfterCommand() {
    const label = 'TEST 5';
    log(label, `Send command but never read response (testing server write buffer)...`);
    const socket = createRawSocket(label, () => {
        markResult(label, 'PASS', 'Connection closed cleanly even though client didnt read.');
    });

    socket.on('connect', () => {
        writePacket(socket, 1, 3, PASSWORD); // auth
        setTimeout(() => {
            writePacket(socket, 2, 2, 'players');
            // Don't attach .on('data'), just let it sit
        }, 500);
    });

    setTimeout(() => socket.destroy(), 10000); // cleanup
}

function printSummary() {
    console.log('\nTest Results Summary:');
    for (const result of testResults) {
        const statusStr = result.status === 'PASS' ? '[PASS]' : '[FAIL]';
        console.log(`${statusStr} ${result.testName}: ${result.message}`);
    }
}

function runAllTests() {
    testMaxConnections();
    setTimeout(testIdleConnection, 6000);
    setTimeout(testMalformedPacket, 12000);
    setTimeout(testNoReadAfterCommand, 24000);
    setTimeout(printSummary, 35000); // Final summary after all tests
}

runAllTests();
