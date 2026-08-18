import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptsDirectory, '..');
const generatedDirectory = resolve(frontendDirectory, 'src', 'app', 'core', 'api', 'generated');

function snapshot(directory) {
  if (!existsSync(directory)) {
    return new Map();
  }

  const files = readdirSync(directory, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => resolve(entry.parentPath, entry.name))
    .sort();

  return new Map(
    files.map((file) => [
      relative(directory, file).replaceAll('\\', '/'),
      createHash('sha256').update(readFileSync(file)).digest('hex'),
    ]),
  );
}

const before = snapshot(generatedDirectory);

const generation = spawnSync(process.execPath, [resolve(scriptsDirectory, 'generate-api.mjs')], {
  cwd: frontendDirectory,
  stdio: 'inherit',
});

if (generation.error) {
  throw generation.error;
}

if (generation.status !== 0) {
  throw new Error(`API generation failed with exit code ${generation.status ?? 'unknown'}.`);
}

const after = snapshot(generatedDirectory);
const changedFiles = [...new Set([...before.keys(), ...after.keys()])].filter(
  (file) => before.get(file) !== after.get(file),
);

if (changedFiles.length > 0) {
  process.stderr.write('The generated API client is out of sync:\n');
  process.stderr.write(`${changedFiles.map((file) => ` - ${file}`).join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write('The generated API client is synchronized.\n');
}
