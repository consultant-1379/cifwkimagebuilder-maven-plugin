#!/bin/bash

ECHO=/bin/echo

OZ_CONFIG=oz.cfg
LOG_LEVEL=3

usage()
{
    ${ECHO} ""
}

test_arg()
{
    if [ -z ${1} ] ; then
        ${ECHO} "${2}"
        exit 2
    fi
}

do_command()
{
    local _cmd_="${1}"
    ${ECHO} "${_cmd_}"
    ${_cmd_}
    _rc_=$?
    if [ ${_rc_} -ne 0 ] ; then
        exit ${_rc_}
    fi
}

if [ $# -eq 0 ] ; then
    usage
    exit 2
fi
ARGS=$(getopt -o v:t:d:n:s:k:u:g: -n "${0}" -- "$@");
if [ $? -ne 0 ] ; then
    #Bad arguments
    exit 1
fi
eval set -- "${ARGS}";

while true ; do
  case "${1}" in
    -v)
        shift
        LOG_LEVEL=${1}
        shift
        ;;
    -t)
        shift
        TDL=${1}
        shift
        ;;
    -d)
        shift
        BUILD_DIR=${1}
        shift
        ;;
    -n)
        shift
        IMAGE_NAME=${1}
        shift
        ;;
    -s)
        shift
        COMP_IMAGE_NAME=${1}
        shift
        ;;
    -k)
        shift
        KICKSTART="-a ${1}"
        shift
        ;;
    -u)
        shift
        OWNER=${1}
        shift
        ;;
    -g)
        shift
        GROUP=${1}
        shift
        ;;
    --)
        shift
        break
        ;;
  esac
done

test_arg "${TDL}" "No TDL specified!"
test_arg "${BUILD_DIR}" "No build dir specified!"
test_arg "${IMAGE_NAME}" "No image name specified!"
test_arg "${COMP_IMAGE_NAME}" "No sparsified image name specified!"
test_arg "${OWNER}" "No owner specified!"
test_arg "${GROUP}" "No group specified!"

INST_CMD="/usr/bin/oz-install -p -d${LOG_LEVEL} -u ${TDL} ${KICKSTART} -c ${BUILD_DIR}/${OZ_CONFIG}"

# check are we on Ubuntu
PLATFORM=$(/usr/bin/python -mplatform)
[[ ${PLATFORM} != *"Ubuntu"* ]] && TEMP_DIR_USE="--check-tmpdir continue --tmp ${BUILD_DIR}"
SPAR_CMD="/usr/bin/virt-sparsify ${TEMP_DIR_USE} --compress ${BUILD_DIR}/images/${IMAGE_NAME} ${BUILD_DIR}/${COMP_IMAGE_NAME}"

do_command "${INST_CMD}"
${ECHO} "Finished install phase."

do_command "${SPAR_CMD}"
${ECHO} "Finished install session."
exit 0
