import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  {
    ignores: [
      'dist',
      'coverage',
      'src/shared/lib/api/schema.d.ts',
      '.tmp-depcruise',
      '.dependency-cruiser.cjs',
    ],
  },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, prettier],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/consistent-type-imports': 'error',
    },
  },
  {
    // Decision tecnica (docs/adr/0004-sin-use-effect.md): sin efectos en la aplicacion
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'react',
              importNames: ['useEffect', 'useLayoutEffect', 'useInsertionEffect'],
              message:
                'No se permite useEffect: deriva estado en el render, usa event handlers, ref callbacks o TanStack Query.',
            },
          ],
        },
      ],
    },
  },
  {
    // fetch/WebSocket solo en el wrapper unico y en tests
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/shared/lib/http-client.ts', 'src/**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'fetch', message: 'Usa shared/lib/http-client.' },
        { name: 'WebSocket', message: 'Usa el adapter STOMP de la feature realtime.' },
      ],
    },
  },
)
