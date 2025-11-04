# Aplicación Android ModbusOpenPLC

Esta es una aplicación Android diseñada para interactuar con un servidor Modbus TCP, permitiendo a los usuarios monitorear entradas discretas y bobinas, y controlar (escribir en) bobinas. La aplicación proporciona una interfaz de usuario sencilla para conectarse a un dispositivo Modbus, mostrar datos Modbus en tiempo real y manejar operaciones Modbus básicas.

## Características

*   **Conexión Modbus TCP:** Establece y gestiona conexiones a servidores Modbus TCP.
*   **Lectura de Entradas Discretas:** Lee y muestra periódicamente el estado de las entradas discretas.
*   **Lectura de Bobinas:** Lee y muestra periódicamente el estado de las bobinas.
*   **Escritura de Bobina Única:** Controla (enciende/apaga) bobinas individuales directamente desde la aplicación.
*   **Conexión Configurable:** Permite establecer la dirección IP del servidor Modbus TCP a través de la interfaz de usuario.
*   **Actualizaciones en Tiempo Real:** Muestra datos Modbus con actualizaciones en tiempo real (cada 1 segundo).
*   **Manejo de Errores:** Proporciona retroalimentación sobre problemas de conexión y errores de comunicación Modbus.

## Tecnologías Utilizadas

*   **Kotlin:** Lenguaje de programación principal.
*   **Android SDK:** Para la construcción de la aplicación Android.
*   **Kotlin Coroutines:** Para programación asíncrona y gestión de operaciones de red.
*   **Librerías AndroidX:** Librerías modernas de desarrollo Android (por ejemplo, `androidx.appcompat`, `androidx.recyclerview`, `com.google.android.material`).
*   **Cliente Modbus TCP Personalizado:** La lógica de comunicación del protocolo Modbus TCP está implementada desde cero dentro de la aplicación.

## Configuración e Instalación

Para configurar y ejecutar este proyecto, necesitarás Android Studio.

1.  **Clonar el repositorio:**
    ```bash
    git clone <url_del_repositorio>
    cd ModbusOpenPLC
    ```
2.  **Abrir en Android Studio:**
    *   Inicia Android Studio.
    *   Selecciona "Abrir un proyecto de Android Studio existente" y navega al directorio `ModbusOpenPLC`.
3.  **Compilar el proyecto:**
    *   Android Studio debería sincronizar y compilar el proyecto automáticamente. Si no, ve a `Build > Make Project`.
4.  **Ejecutar en un dispositivo o emulador:**
    *   Conecta un dispositivo Android o inicia un emulador de Android.
    *   Haz clic en el botón "Run" (triángulo verde) en Android Studio para desplegar la aplicación.

## Uso

1.  **Ingresar IP del Servidor Modbus:** En la pantalla principal, ingresa la dirección IP de tu servidor Modbus TCP en el campo de entrada provisto. La IP predeterminada es `192.168.100.195`.
2.  **Conectar:** Toca el botón "Conectar" para establecer una conexión con el servidor Modbus. Se mostrará el estado de la conexión.
3.  **Monitorear Datos:** Una vez conectado, la aplicación comenzará a leer y mostrar periódicamente el estado de las entradas discretas y las bobinas.
4.  **Controlar Bobinas:** Para cambiar el estado de una bobina, toca su representación en la sección "Bobinas". Esto enviará una solicitud de escritura al servidor Modbus.
5.  **Desconectar:** Toca el botón "Desconectar" para cerrar la conexión Modbus TCP.

## Parámetros Modbus

*   **Dirección IP Predeterminada:** `192.168.100.195`
*   **Puerto Modbus TCP:** `502`
*   **ID de Unidad:** `7` (utilizado para todas las solicitudes Modbus)
*   **Rango de Entradas Discretas/Bobinas:** La aplicación lee/escribe 8 entradas discretas y 8 bobinas comenzando desde la dirección 1.

## Estructura del Proyecto (Archivos Clave)

*   `app/src/main/java/com/example/modbus_openplc/MainActivity.kt`: La actividad principal que maneja la interfaz de usuario, la gestión de la conexión y la visualización de datos.
*   `app/src/main/java/com/example/modbus_openplc/ModbusTcpClient.kt`: Implementación personalizada del cliente Modbus TCP para la comunicación.
*   `app/src/main/java/com/example/modbus_openplc/ModbusValueAdapter.kt`: Adaptador para mostrar valores Modbus (entradas discretas y bobinas) en `RecyclerView`s.
*   `app/src/main/res/layout/activity_main.xml`: Archivo de diseño para la actividad principal.
*   `app/build.gradle.kts`: Dependencias del proyecto y configuración de compilación.