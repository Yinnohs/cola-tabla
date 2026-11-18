import { afterAll, describe, expect, it } from 'vitest'
import { spawnSync } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

const root = resolve(__dirname, '..')
const tmp = join(root, '.tmp-depcruise')
const config = join(root, '.dependency-cruiser.cjs')
const bin = join(root, 'node_modules', 'dependency-cruiser', 'bin', 'dependency-cruiser.mjs')

type Files = Record<string, string>

function cruise(name: string, files: Files) {
  const dir = join(tmp, name)
  rmSync(dir, { recursive: true, force: true })
  for (const [rel, content] of Object.entries(files)) {
    const full = join(dir, rel)
    mkdirSync(dirname(full), { recursive: true })
    writeFileSync(full, content)
  }
  writeFileSync(
    join(dir, 'tsconfig.json'),
    '{"compilerOptions":{"moduleResolution":"bundler","module":"esnext","target":"es2022"}}',
  )
  const res = spawnSync(
    process.execPath,
    [bin, 'src', '--config', config, '--output-type', 'err'],
    {
      cwd: dir,
      encoding: 'utf8',
    },
  )
  return { status: res.status, out: `${res.stdout}${res.stderr}` }
}

afterAll(() => rmSync(tmp, { recursive: true, force: true }))

const violations: Array<[string, string, Files]> = [
  [
    'domain-is-pure',
    'domain importa otra capa del proyecto',
    {
      'src/features/cards/domain/card.ts': `import { x } from '../infrastructure/y'\nexport const c = x\n`,
      'src/features/cards/infrastructure/y.ts': `export const x = 1\n`,
    },
  ],
  [
    'domain-no-framework-packages',
    'domain importa React',
    {
      'src/features/cards/domain/card.ts': `import { useState } from 'react'\nexport const c = useState\n`,
    },
  ],
  [
    'domain-no-framework-packages',
    'domain importa TanStack',
    {
      'src/features/cards/domain/card.ts': `import { QueryClient } from '@tanstack/react-query'\nexport const c = QueryClient\n`,
    },
  ],
  [
    'application-no-infrastructure-or-ui',
    'application importa infrastructure',
    {
      'src/features/cards/application/uc.ts': `import { x } from '../infrastructure/y'\nexport const u = x\n`,
      'src/features/cards/infrastructure/y.ts': `export const x = 1\n`,
    },
  ],
  [
    'ui-no-infrastructure',
    'ui importa infrastructure',
    {
      'src/features/cards/ui/v.ts': `import { x } from '../infrastructure/y'\nexport const v = x\n`,
      'src/features/cards/infrastructure/y.ts': `export const x = 1\n`,
    },
  ],
  [
    'infrastructure-no-ui',
    'infrastructure importa ui',
    {
      'src/features/cards/infrastructure/y.ts': `import { v } from '../ui/v'\nexport const x = v\n`,
      'src/features/cards/ui/v.ts': `export const v = 1\n`,
    },
  ],
  [
    'feature-to-feature-only-via-index',
    'feature importa interno de otra feature',
    {
      'src/features/boards/application/uc.ts': `import { c } from '../../cards/domain/card'\nexport const u = c\n`,
      'src/features/cards/domain/card.ts': `export const c = 1\n`,
    },
  ],
  [
    'shared-no-features',
    'shared importa features',
    {
      'src/shared/lib/h.ts': `import { c } from '../../features/cards'\nexport const h = c\n`,
      'src/features/cards/index.ts': `export const c = 1\n`,
    },
  ],
  [
    'dnd-kit-only-in-ui',
    'application importa dnd-kit',
    {
      'src/features/cards/application/uc.ts': `import { DndContext } from '@dnd-kit/core'
export const u = DndContext
`,
    },
  ],
  [
    'dnd-kit-only-in-ui',
    'shared importa dnd-kit',
    {
      'src/shared/ui/x.ts': `import { DndContext } from '@dnd-kit/core'
export const x = DndContext
`,
    },
  ],
  [
    'api-client-only-infrastructure',
    'application importa el cliente generado',
    {
      'src/features/cards/application/uc.ts': `import { a } from '../../../shared/lib/api'
export const u = a
`,
      'src/shared/lib/api/index.ts': `export const a = 1
`,
    },
  ],
  [
    'api-client-only-infrastructure',
    'ui importa el cliente generado',
    {
      'src/features/cards/ui/v.ts': `import { a } from '../../../shared/lib/api'
export const v = a
`,
      'src/shared/lib/api/index.ts': `export const a = 1
`,
    },
  ],
]

describe('reglas de arquitectura (dependency-cruiser)', () => {
  it.each(violations)(
    'falla: %s (%s)',
    (rule, _desc, files) => {
      const { status, out } = cruise(`v-${rule}-${_desc.replace(/\W+/g, '-')}`, files)
      expect(status).not.toBe(0)
      expect(out).toContain(rule)
    },
    30_000,
  )

  it('pasa: dnd-kit importado desde features/<x>/ui/dnd', () => {
    const { status, out } = cruise('ok-dnd', {
      'src/features/cards/ui/dnd/b.ts': `import { DndContext } from '@dnd-kit/core'
export const b = DndContext
`,
    })
    expect(out).not.toMatch(/error/i)
    expect(status).toBe(0)
  }, 30_000)

  it('pasa: feature->feature via index.ts, application->domain, ui->application', () => {
    const { status, out } = cruise('ok', {
      'src/features/cards/index.ts': `export { c } from './domain/card'\n`,
      'src/features/cards/domain/card.ts': `export const c = 1\n`,
      'src/features/boards/application/uc.ts': `import { c } from '../../cards'\nexport const u = c\n`,
      'src/features/boards/ui/v.ts': `import { u } from '../application/uc'\nexport const v = u\n`,
    })
    expect(out).not.toMatch(/error/i)
    expect(status).toBe(0)
  }, 30_000)
})
