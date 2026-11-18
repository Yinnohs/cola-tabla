/** Reglas de arquitectura (initial-plan.md seccion 5.2). No desactivar: si estorban, escribir un ADR. */
const FEATURE = '^src/features/([^/]+)'

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'domain-is-pure',
      comment:
        'domain solo puede depender de su propio domain y shared/kernel (sin React/TanStack/stomp/dnd).',
      severity: 'error',
      from: { path: `${FEATURE}/domain/` },
      to: {
        pathNot: [`^src/features/$1/domain/`, '^src/shared/kernel/'],
        path: '^src/',
      },
    },
    {
      name: 'domain-no-framework-packages',
      comment: 'domain sin React, TanStack, stomp ni dnd-kit.',
      severity: 'error',
      from: { path: `${FEATURE}/domain/` },
      to: { path: ['node_modules/(react|react-dom|@tanstack|@stomp|@dnd-kit)(/|$)'] },
    },
    {
      name: 'application-no-infrastructure-or-ui',
      comment: 'application nunca importa infrastructure ni ui.',
      severity: 'error',
      from: { path: `${FEATURE}/application/` },
      to: { path: `${FEATURE}/(infrastructure|ui)/` },
    },
    {
      name: 'application-no-transport-packages',
      comment: 'application sin stomp directo (los puertos lo abstraen).',
      severity: 'error',
      from: { path: `${FEATURE}/application/` },
      to: { path: ['node_modules/@stomp(/|$)'] },
    },
    {
      name: 'infrastructure-no-ui',
      comment: 'infrastructure nunca importa ui.',
      severity: 'error',
      from: { path: `${FEATURE}/infrastructure/` },
      to: { path: `${FEATURE}/ui/` },
    },
    {
      name: 'ui-no-infrastructure',
      comment: 'ui nunca importa infrastructure.',
      severity: 'error',
      from: { path: `${FEATURE}/ui/` },
      to: { path: `${FEATURE}/infrastructure/` },
    },
    {
      name: 'feature-to-feature-only-via-index',
      comment: 'Una feature solo puede importar otra a traves de features/<otra>/index.ts.',
      severity: 'error',
      from: { path: `${FEATURE}/` },
      to: {
        path: '^src/features/',
        pathNot: ['^src/features/$1/', '^src/features/[^/]+/index\\.ts$'],
      },
    },
    {
      name: 'shared-no-features',
      comment: 'shared/* nunca importa de features/.',
      severity: 'error',
      from: { path: '^src/shared/' },
      to: { path: '^src/features/' },
    },
    {
      name: 'shared-no-app',
      comment: 'shared no depende de app (salvo shared/testing, que cablea el PortsProvider).',
      severity: 'error',
      from: { path: '^src/shared/', pathNot: '^src/shared/testing/' },
      to: { path: '^src/app/' },
    },
    {
      name: 'features-only-app-di',
      comment: 'Las features solo pueden usar de app/ el modulo di (usePorts).',
      severity: 'error',
      from: { path: '^src/features/' },
      to: { path: '^src/app/', pathNot: '^src/app/di/' },
    },
    {
      name: 'dnd-kit-only-in-ui',
      comment:
        'dnd-kit solo se importa desde la capa ui de una feature (la integracion vive en features/cards/ui/dnd).',
      severity: 'error',
      from: { path: '^src/', pathNot: '^src/features/[^/]+/ui/' },
      to: { path: ['node_modules/@dnd-kit(/|$)'] },
    },
    {
      name: 'api-client-only-infrastructure',
      comment:
        'El cliente generado shared/lib/api solo lo importan infrastructure (y el composition root app/di).',
      severity: 'error',
      from: {
        path: '^src/',
        pathNot: ['^src/features/[^/]+/infrastructure/', '^src/shared/lib/api/', '^src/app/di/'],
      },
      to: { path: '^src/shared/lib/api/' },
    },
    {
      name: 'no-test-helpers-in-prod',
      comment: 'Codigo de produccion no importa shared/testing.',
      severity: 'error',
      from: { path: '^src/', pathNot: ['^src/shared/testing/', '\\.test\\.tsx?$'] },
      to: { path: '^src/shared/testing/' },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    tsPreCompilationDeps: true,
    tsConfig: { fileName: 'tsconfig.json' },
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default', 'types'],
      mainFields: ['module', 'main', 'types', 'typings'],
    },
  },
}
