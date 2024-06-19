package ee.openeid.siga;

import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.digidoc4j.Container;
import org.digidoc4j.SignatureProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer", "mobileId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
class MobileIdDatafileApplicationTests extends MobileIdBaseApplicationTests {

    @Test
    void mobileIdDatafileSigningFlow() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(1, originalContainer.getDataFiles().size());
        List<DataFile> dataFiles = getDataFiles(containerId);
        assertEquals(1, dataFiles.size());

        String signatureId = startMobileSigning(containerId);
        String mobileFirstStatus = getMobileIdStatus(containerId, signatureId);
        assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(15, SECONDS).until(isMobileIdResponseSuccessful(containerId, signatureId));

        assertSignedContainer(containerId, 2);

        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=MID_GET_MOBILE_SIGN_HASH_STATUS, client_name=client1, " +
                        "client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, mid_status=SIGNATURE,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://tsa.demo.sk.ee/tsa, .*result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/esteid2015,.* result=SUCCESS.*");
    }

    @Test
    void mobileIdDatafileAugmentingSignaturesFlow() throws Exception {
        String containerId = createContainer();

        String signatureId = startMobileSigning(containerId);
        String mobileFirstStatus = getMobileIdStatus(containerId, signatureId);
        assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(15, SECONDS).until(isMobileIdResponseSuccessful(containerId, signatureId));

        Container container = getContainer(containerId);
        assertEquals(1, container.getSignatures().size());
        assertEquals(1, container.getDataFiles().size());

        List<Signature> signatures = getSignatures(containerId);
        assertEquals(1, signatures.size());
        assertEquals(SignatureProfile.LT.name(), signatures.get(0).getSignatureProfile());
        ValidationConclusion validationConclusion = getValidationConclusion(containerId);
        assertEquals(1, validationConclusion.getValidSignaturesCount());
        assertEquals(1, validationConclusion.getSignaturesCount());

        augmentContainer(containerId);

        List<Signature> augmentedSignatures = getSignatures(containerId);
        assertEquals(1, augmentedSignatures.size());
        assertEquals(SignatureProfile.LTA.name(), augmentedSignatures.get(0).getSignatureProfile());
        ValidationConclusion augmentedSignatureValidationConclusion = getValidationConclusion(containerId);
        assertEquals(1, augmentedSignatureValidationConclusion.getValidSignaturesCount());
        assertEquals(1, augmentedSignatureValidationConclusion.getSignaturesCount());

        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=MID_GET_MOBILE_SIGN_HASH_STATUS, client_name=client1, " +
                        "client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, mid_status=SIGNATURE,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://tsa.demo.sk.ee/tsa, .*result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/esteid2015,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://tsa.demo.sk.ee/tsa, .*result=SUCCESS.*");
    }

}
