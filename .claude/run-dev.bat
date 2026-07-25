@echo off
REM Arranca la app en el perfil dev (H2 en memoria, sin MySQL ni Docker) en :8080.
REM Lo usa .claude/launch.json; también sirve para arrancarla a mano.
REM Existe porque mvnw.cmd necesita JAVA_HOME y el proceso que lo lanza no siempre
REM lo hereda del entorno del usuario.

if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"

REM %~dp0 es .claude\ dentro del repo; el pom.xml está un nivel por encima.
cd /d "%~dp0.."

REM Ruta explícita al wrapper: este equipo tiene deshabilitada la búsqueda de
REM ejecutables en el directorio actual, así que "call mvnw.cmd" no lo encuentra.
call "%~dp0..\mvnw.cmd" -B spring-boot:run
