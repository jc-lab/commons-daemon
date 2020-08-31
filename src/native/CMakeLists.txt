cmake_minimum_required(VERSION 3.8)
project(commons-daemon-native)

set(JAVA_AWT_LIBRARY NotNeeded)
set(JAVA_AWT_INCLUDE_PATH NotNeeded)
find_package(JNI REQUIRED)

string(TOLOWER "${CMAKE_SYSTEM_PROCESSOR}" temp_arch)
if (temp_arch MATCHES "(x86_64|AMD64)")
    set(HOST_CPU "i386")
elseif(temp_arch MATCHES "(i.?86|x86)")
    set(HOST_CPU "amd64")
elseif(temp_arch MATCHES "(mips.*el)")
    set(HOST_CPU "mipsel")
elseif(temp_arch MATCHES "(mips.*)")
    set(HOST_CPU "mips")
elseif(temp_arch MATCHES "powerpc64")
    set(HOST_CPU "ppc64")
elseif(temp_arch MATCHES "powerpc64le")
    set(HOST_CPU "ppc64le")
elseif(temp_arch MATCHES "powerpc.*")
    set(HOST_CPU "${temp_arch}")
elseif(temp_arch MATCHES "sparc.*")
    set(HOST_CPU "${temp_arch}")
elseif(temp_arch MATCHES "arm.*")
    set(HOST_CPU "arm")
elseif(temp_arch MATCHES "aarch64")
    set(HOST_CPU "aarch64")
endif()

message( STATUS "Architecture: ${CMAKE_SYSTEM_PROCESSOR}" )

list(REMOVE_ITEM JNI_LIBRARIES "NotNeeded")
list(REMOVE_ITEM JNI_INCLUDE_DIRS "NotNeeded")

if (MSVC)
    set(PLAT_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/windows)

    set(PRUNSRV_MANIFEST_FILE ${PLAT_SRC_DIR}/prunsrv/prunsrv.manifest)
    set(PRUNSRV_SRC_FILES
            ${PLAT_SRC_DIR}/apps/prunsrv/prunsrv.c
            ${PLAT_SRC_DIR}/apps/prunsrv/prunsrv.h
            ${PLAT_SRC_DIR}/src/cmdline.c
            ${PLAT_SRC_DIR}/src/console.c
            ${PLAT_SRC_DIR}/src/gui.c
            ${PLAT_SRC_DIR}/src/log.c
            ${PLAT_SRC_DIR}/src/handles.c
            ${PLAT_SRC_DIR}/src/javajni.c
            ${PLAT_SRC_DIR}/src/mclib.c
            ${PLAT_SRC_DIR}/src/registry.c
            ${PLAT_SRC_DIR}/src/rprocess.c
            ${PLAT_SRC_DIR}/src/service.c
            ${PLAT_SRC_DIR}/src/utils.c

            ${PLAT_SRC_DIR}/include/apxwin.h
            ${PLAT_SRC_DIR}/include/cmdline.h
            ${PLAT_SRC_DIR}/include/console.h
            ${PLAT_SRC_DIR}/include/gui.h
            ${PLAT_SRC_DIR}/include/log.h
            ${PLAT_SRC_DIR}/include/registry.h
            ${PLAT_SRC_DIR}/include/rprocess.h
            ${PLAT_SRC_DIR}/include/service.h

            ${CMAKE_CURRENT_BINARY_DIR}/prunsrv.res
            )

    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /MANIFEST:NO")
    add_executable(prunsrv ${PRUNSRV_SRC_FILES})
    target_compile_definitions(prunsrv
            PRIVATE
            -DUNICODE -D_UNICODE
            -DHOST_CPU=${HOST_CPU}
            -DCPU=\"${HOST_CPU}\"
            )
    target_include_directories(prunsrv
            PRIVATE
            ${JNI_INCLUDE_DIRS}
            ${PLAT_SRC_DIR}/include
            ${PLAT_SRC_DIR}/src
            )
    target_link_libraries(prunsrv
            PRIVATE
            user32.lib gdi32.lib winspool.lib comdlg32.lib comctl32.lib shlwapi.lib
            ${JNI_LIBRARIES}
            )

    add_custom_command(
            OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/prunsrv.res
            COMMAND ${CMAKE_RC_COMPILER} ${CMAKE_RC_FLAGS} /i "${PLAT_SRC_DIR}/include" /fo "${CMAKE_CURRENT_BINARY_DIR}/prunsrv.res" "${PLAT_SRC_DIR}/apps/prunsrv/prunsrv.rc"
            COMMENT "Building RC..."
    )

    if (EXISTS PRUNSRV_MANIFEST_FILE)
        add_custom_command(
                TARGET prunsrv
                POST_BUILD
                COMMAND mt -nologo -manifest "${PRUNSRV_MANIFEST_FILE}" -outputresource:"$(TargetDir)$(TargetFileName)"\;\#1
                COMMENT "Adding manifest..."
        )
    endif()

    install(TARGETS prunsrv
            RUNTIME DESTINATION "."
            )
else()
    set(PLAT_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/unix/native)
    set(LIBSERVICE_SRC_FILES
            ${PLAT_SRC_DIR}/arguments.c
            ${PLAT_SRC_DIR}/debug.c
            ${PLAT_SRC_DIR}/dso-dlfcn.c
            ${PLAT_SRC_DIR}/dso-dyld.c
            ${PLAT_SRC_DIR}/help.c
            ${PLAT_SRC_DIR}/home.c
            ${PLAT_SRC_DIR}/java.c
            ${PLAT_SRC_DIR}/location.c
            ${PLAT_SRC_DIR}/replace.c
            ${PLAT_SRC_DIR}/locks.c
            ${PLAT_SRC_DIR}/signals.c

            ${PLAT_SRC_DIR}/arguments.h
            ${PLAT_SRC_DIR}/debug.h
            ${PLAT_SRC_DIR}/dso.h
            ${PLAT_SRC_DIR}/help.h
            ${PLAT_SRC_DIR}/home.h
            ${PLAT_SRC_DIR}/java.h
            ${PLAT_SRC_DIR}/jsvc.h
            ${PLAT_SRC_DIR}/location.c
            ${PLAT_SRC_DIR}/locks.h
            ${PLAT_SRC_DIR}/replace.h
            ${PLAT_SRC_DIR}/signals.h
            ${PLAT_SRC_DIR}/version.h
            )
    set(JSVC_SRC_FILES
            ${PLAT_SRC_DIR}/jsvc-unix.c
            )

    add_library(service STATIC ${LIBSERVICE_SRC_FILES})
    target_compile_definitions(service
            PRIVATE
            -DHOST_CPU=${HOST_CPU}
            -DCPU=\"${HOST_CPU}\"
            -DOS_LINUX
            -DDSO_DLFCN
            )
    target_include_directories(service
            PRIVATE
            ${JNI_INCLUDE_DIRS}
            )

    add_executable(jsvc ${JSVC_SRC_FILES})
    target_include_directories(jsvc
            PRIVATE
            ${JNI_INCLUDE_DIRS}
            )
    target_link_libraries(jsvc
            PRIVATE
            service
            ${JNI_LIBRARIES}
            pthread
            dl
            )

    install(TARGETS jsvc
            RUNTIME DESTINATION "."
            )
endif()
