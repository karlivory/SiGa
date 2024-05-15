package ee.openeid.siga.common.event;

public enum SigaEventName {
    REQUEST,
    AUTHENTICATION,
    HC_UPLOAD_CONTAINER,
    HC_CREATE_CONTAINER,
    HC_VALIDATE_CONTAINER,
    HC_VALIDATE_CONTAINER_BY_ID,
    HC_REMOTE_SIGNING_INIT,
    HC_REMOTE_SIGNING_FINISH,
    HC_MOBILE_ID_SIGNING_INIT,
    HC_MOBILE_ID_SIGNING_STATUS,
    HC_SMART_ID_CERTIFICATE_CHOICE_INIT,
    HC_SMART_ID_CERTIFICATE_CHOICE_STATUS,
    HC_SMART_ID_SIGNING_INIT,
    HC_SMART_ID_SIGNING_STATUS,
    HC_GET_SIGNATURES_LIST,
    HC_GET_SIGNATURE,
    HC_ADD_DATAFILE,
    HC_DELETE_DATAFILE,
    HC_GET_DATAFILES_LIST,
    HC_GET_CONTAINER,
    HC_DELETE_CONTAINER,
    UPLOAD_CONTAINER,
    CREATE_CONTAINER,
    VALIDATE_CONTAINER,
    VALIDATE_CONTAINER_BY_ID,
    REMOTE_SIGNING_INIT,
    REMOTE_SIGNING_FINISH,
    MOBILE_ID_SIGNING_INIT,
    MOBILE_ID_SIGNING_STATUS,
    SMART_ID_CERTIFICATE_CHOICE_INIT,
    SMART_ID_CERTIFICATE_CHOICE_STATUS,
    SMART_ID_SIGNING_INIT,
    SMART_ID_SIGNING_STATUS,
    GET_SIGNATURES_LIST,
    GET_SIGNATURE,
    AUGMENT_SIGNATURES,
    ADD_DATAFILE,
    GET_DATAFILES_LIST,
    DELETE_DATAFILE,
    GET_CONTAINER,
    DELETE_CONTAINER,
    FINALIZE_SIGNATURE,
    OCSP_REQUEST,
    TSA_REQUEST,
    MID_GET_MOBILE_CERTIFICATE,
    MID_MOBILE_SIGN_HASH,
    MID_GET_MOBILE_SIGN_HASH_STATUS,
    SMART_ID_GET_SIGN_HASH_STATUS,
    SMART_ID_GET_CERT_STATUS,
    SMART_ID_GET_CERTIFICATE,
    SMART_ID_GET_CERTIFICATE_STATUS,
    SMART_ID_SIGN_HASH,
    SMART_ID_CERTIFICATE_CHOICE;

    public enum ErrorCode {
        AUTHENTICATION_ERROR,
        SIGNATURE_FINALIZING_ERROR,
        SIGNATURE_FINALIZING_REQUEST_ERROR
    }

    public enum EventParam {
        CONTAINER_ID,
        REQUEST_URL,
        SIGNATURE_ID,
        ISSUING_CA,
        MID_REST_RESULT,
        SESSION_CODE,
        SIGN_CERT_STATUS,
        PERSON_IDENTIFIER,
        COUNTRY,
        PHONE_NR,
        RELYING_PARTY_NAME,
        SIGNATURE_PROFILE,
        NO_OF_DATAFILES
    }
}
