'use strict';
const { execSync } = require('child_process');
const path = require('path');

const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const serverDir = path.join(__dirname, '..', 'server');

execSync(`${mvnw} package -DskipTests`, { cwd: serverDir, stdio: 'inherit' });
