# Bitavida

Bitavida es una aplicación Android para organizar información de cuidado entre consultas. Su propósito es registrar y presentar información; no diagnostica, interpreta mediciones, calcula dosis ni recomienda tratamientos.

## Estado actual

**BITAVIDA UI BASELINE 0.5.4** es la referencia visual oficial. El módulo `:app` contiene una demostración Compose con datos ficticios y estado efímero. La composición de “Mi día”, los temas claro y oscuro, Kodchasan, la identidad “El hilo de la vida”, la estela luminosa, los nodos, las microinteracciones y la navegación inferior están implementados.

Esta baseline no equivale a una versión clínica funcional. Aún no existen persistencia de datos de cuidado, cuentas, sincronización, backend, exportación, respaldos del producto ni funciones de asistencia. La aplicación no solicita acceso a Internet.

## Compilación

Requisitos: Android Studio compatible con el proyecto y JDK 11.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Estructura

- `app/src/main/java/com/bitavida/app/ui`: demo, componentes, marca y tema.
- `app/src/main/java/com/bitavida/app/domain`: tipos de dominio puros ya definidos.
- `app/src/test`: pruebas unitarias de geometría y continuidad.
- `app/src/androidTest`: recorridos e interacción Compose, ejecutados solo cuando se solicita un dispositivo.
- `docs/reference`: reporte maestro original.
- `docs/licenses`: licencias obligatorias.

## Documentación

- [Producto](docs/PRODUCT.md)
- [Arquitectura](docs/ARCHITECTURE.md)
- [Modelo de dominio](docs/DOMAIN_MODEL.md)
- [Seguridad](docs/SECURITY.md)
- [Sistema de diseño](docs/DESIGN_SYSTEM.md)
- [Accesibilidad](docs/ACCESSIBILITY.md)
- [Asistencia prevista](docs/ASSISTANCE.md)
- [Reglas para agentes](AGENTS.md)

El reporte maestro es la fuente funcional. La baseline 0.5.4 es la fuente visual. Cualquier cambio de alcance o de diseño requiere autorización y actualización documental previa.
