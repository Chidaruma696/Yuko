# Avisos de terceros

Yuko incorpora o depende del siguiente software de terceros. Cada uno conserva su licencia y su copyright.

## Fuentes

| Componente | Uso en Yuko | Licencia |
| --- | --- | --- |
| [kotatsu-parsers](https://github.com/kotatsu-redo/kotatsu-parsers-redo) (comunidad de Kotatsu, fork mantenido por kotatsu-redo) | Los parsers de cada sitio de manga, compilados dentro de la app como dependencia. Yuko aporta el `MangaLoaderContext`, el cliente HTTP, las cookies, el paso de Cloudflare y el motor JavaScript en `manga-sources/` | MIT |

Yuko no reutiliza código de la aplicación Kotatsu ni de Futon (GPL-3): la interfaz, el lector, la biblioteca y las descargas son propios.

## Diseño

| Componente | Uso en Yuko | Licencia |
| --- | --- | --- |
| [Komi Store](https://github.com/komi-store/komi-store) (kurikomi-labs) | Sistema de diseño *personality* "Manga": componentes, decoraciones de tinta, tipografía y forma. Adaptado en `app/src/main/kotlin/com/yuko/app/ui/komi` | Apache 2.0 |
| [Anton](https://fonts.google.com/specimen/Anton) (Vernon Adams) | Titulares | SIL Open Font License 1.1 |
| [Noto Sans](https://fonts.google.com/noto) (Google) | Cuerpo de texto | SIL Open Font License 1.1 |
| [JetBrains Mono](https://www.jetbrains.com/lp/mono/) (JetBrains) | Texto monoespaciado | SIL Open Font License 1.1 |
| [Material Design Icons](https://fonts.google.com/icons) (Google) | Iconos de las pestañas | Apache 2.0 |

Las paletas de color son propias de Yuko. Sus nombres corresponden a personajes de Touhou Project, propiedad de Team Shanghai Alice; no existe afiliación ni respaldo.

## Bibliotecas

| Componente | Licencia |
| --- | --- |
| [OkHttp](https://github.com/square/okhttp) | Apache 2.0 |
| [Coil](https://github.com/coil-kt/coil) | Apache 2.0 |
| [Jsoup](https://jsoup.org/) | MIT |
| [Jetpack Compose y AndroidX](https://developer.android.com/jetpack) | Apache 2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache 2.0 |
| [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) | GPL 2.0 con excepción de classpath |

## Textos de licencia

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- MIT License: <https://opensource.org/license/mit>
- SIL Open Font License 1.1: <https://openfontlicense.org/open-font-license-official-text/>
