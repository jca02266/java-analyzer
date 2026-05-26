'use strict';
const { execSync } = require('child_process');
const path = require('path');

// Check Java version (requires 11+)
let javaVersion;
try {
    const output = execSync('java -version 2>&1').toString();
    const match = output.match(/version "(\d+)(?:\.(\d+))?/);
    if (match) {
        javaVersion = parseInt(match[1], 10);
        if (javaVersion === 1) javaVersion = parseInt(match[2], 10); // 1.8 -> 8
    }
} catch {
    console.error('ERROR: "java" command not found. Please install JDK 11 or later.');
    process.exit(1);
}

if (!javaVersion || javaVersion < 11) {
    console.error(`ERROR: Java 11 or later is required (found Java ${javaVersion}).`);
    console.error('       lsp4j 0.23.1 requires Java 11+.');
    console.error('       Please install JDK 11 or later: https://adoptium.net/');
    process.exit(1);
}

console.log(`Java ${javaVersion} detected. Building server...`);

const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const serverDir = path.join(__dirname, '..', 'server');
execSync(`${mvnw} package -DskipTests`, { cwd: serverDir, stdio: 'inherit' });
