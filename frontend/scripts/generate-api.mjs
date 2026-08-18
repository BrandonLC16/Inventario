import { existsSync, readFileSync, rmSync } from 'node:fs';
import { delimiter, dirname, join, relative, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptsDirectory, '..');
const contractPath = resolve(frontendDirectory, '..', 'target', 'openapi', 'inventory-api-v1.json');
const generatedDirectory = resolve(frontendDirectory, 'src', 'app', 'core', 'api', 'generated');
const expectedGeneratedPath = 'src/app/core/api/generated';
const relativeGeneratedPath = relative(frontendDirectory, generatedDirectory).replaceAll('\\', '/');

if (relativeGeneratedPath !== expectedGeneratedPath) {
  throw new Error(`Refusing to replace unexpected directory: ${generatedDirectory}`);
}

if (!existsSync(contractPath)) {
  throw new Error(
    `OpenAPI contract not found at ${contractPath}. Generate it from the backend before running this command.`,
  );
}

const contract = JSON.parse(readFileSync(contractPath, 'utf8'));
if (contract.openapi !== '3.1.0' || contract.info?.version !== 'v1') {
  throw new Error('The canonical contract must be OpenAPI 3.1.0 with info.version v1.');
}

const cliPath = join(
  frontendDirectory,
  'node_modules',
  '@openapitools',
  'openapi-generator-cli',
  'main.js',
);
const javaHome = process.env['JAVA_HOME'];
const javaBin = javaHome ? join(javaHome, 'bin') : undefined;
const environment = {
  ...process.env,
  PATH: javaBin ? `${javaBin}${delimiter}${process.env['PATH'] ?? ''}` : process.env['PATH'],
};

rmSync(generatedDirectory, { recursive: true, force: true });

const result = spawnSync(
  process.execPath,
  [
    cliPath,
    'generate',
    '--input-spec',
    contractPath,
    '--generator-name',
    'typescript-angular',
    '--output',
    generatedDirectory,
    '--config',
    join(frontendDirectory, 'openapi-generator-config.json'),
    '--global-property',
    'apiDocs=false,apiTests=false,modelDocs=false,modelTests=false',
  ],
  { cwd: frontendDirectory, env: environment, stdio: 'inherit' },
);

if (result.error) {
  throw result.error;
}

if (result.status !== 0) {
  throw new Error(`OpenAPI Generator failed with exit code ${result.status ?? 'unknown'}.`);
}
