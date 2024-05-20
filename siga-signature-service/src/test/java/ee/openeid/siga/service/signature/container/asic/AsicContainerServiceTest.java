package ee.openeid.siga.service.signature.container.asic;


import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.ContainerInfo;
import ee.openeid.siga.common.model.DataFile;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.Signature;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.spi.x509.tsp.TimestampToken;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.impl.asic.AsicSignature;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static ee.openeid.siga.service.signature.test.RequestUtil.CLIENT_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_SESSION_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_UUID;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE;
import static ee.openeid.siga.service.signature.test.RequestUtil.createAsicSessionHolder;
import static ee.openeid.siga.service.signature.test.RequestUtil.createDataFileListWithOneFile;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AsicContainerServiceTest {

    private static final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ECC_from_TEST_of_ESTEID2018.p12", "1234".toCharArray());
    private static final Map<String, String> notAugmentableContainers = Map.of(
            "B_EPES", "bdoc-with-b-epes-signature.bdoc", // Contains a single B_EPES signature
            "LT_TM", "bdoc-with-tm-and-ts-signature.bdoc", // Contains one LT and one LT_TM signature
            "T", "T_level_signature.asice" // Contains a single T level signature (LT signature with removed OCSP)
    );

    @Spy
    @InjectMocks
    private AsicContainerService containerService;

    @Mock
    private SessionService sessionService;
    @Mock
    private SigaEventLogger eventLogger;

    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);

    @Captor
    private ArgumentCaptor<Session> sessionCaptor;

    @BeforeEach
    void setUp() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.lenient().when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f").build());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        Mockito.lenient().doReturn(CONTAINER_ID).when(containerService).generateContainerId();
        Mockito.lenient().when(sessionService.getSessionId(CONTAINER_ID)).thenReturn(CONTAINER_SESSION_ID);
    }

    @Test
    void successfulCreateContainer() {
        List<DataFile> dataFiles = createDataFileListWithOneFile();

        String containerId = containerService.createContainer("test.asice", dataFiles);
        assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    void successfulUploadContainer() throws Exception {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(VALID_ASICE).readAllBytes()));
        String containerId = containerService.uploadContainer("test.asice", container);
        assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    void successfulGetContainer() throws Exception {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        ContainerInfo containerInfo = containerService.getContainer(CONTAINER_ID);
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(containerInfo.getContainer().getBytes()));
        Container container = ContainerBuilder.aContainer(ASICE).fromStream(inputStream).build();
        assertEquals(1, container.getSignatures().size());
    }

    @Test
    void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        List<Signature> signatures = containerService.getSignatures(CONTAINER_ID);
        assertEquals("S0", signatures.get(0).getId());
        assertEquals("LT", signatures.get(0).getSignatureProfile());
        assertFalse(StringUtils.isBlank(signatures.get(0).getGeneratedSignatureId()));
        assertEquals("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE", signatures.get(0).getSignerInfo());
    }

    @Test
    void successfulGetSignature() throws IOException, URISyntaxException {
        AsicContainerSession session = createAsicSessionHolder();
        AtomicReference<String> signatureId = new AtomicReference<>();
        session.getSignatureIdHolder().forEach((sigId, integer) -> signatureId.set(sigId));
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        org.digidoc4j.Signature signature = containerService.getSignature(CONTAINER_ID, signatureId.get());
        assertEquals("S0", signature.getId());
    }

    @Test
    void successfulGetDataFiles() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);
        assertEquals("test.xml", dataFiles.get(0).getFileName());
        assertEquals("PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHByb2plY3QgeG1sbnM6amFjb2NvPSJhbnRsaWI6b3JnLmphY29jby5hbnQiIG5hbWU9ImRpZ2lkb2M0aiBidWlsZGVyIiBiYXNlZGlyPSIuLi8iIGRlZmF1bHQ9ImFsbCI+CiAgICA8cHJvcGVydHkgbmFtZT0iYnVpbGQuZGlyIiB2YWx1ZT0iYnVpbGQiLz4KICAgIDxwcm9wZXJ0eSBuYW1lPSJjb3ZlcmFnZS5kaXIiIHZhbHVlPSJjb3ZlcmFnZSIvPgogICAgPHByb3BlcnR5IG5hbWU9InZlcnNpb24iIHZhbHVlPSIwLjIiLz4KCiAgICA8dGFza2RlZiB1cmk9ImFudGxpYjpvcmcuamFjb2NvLmFudCIgcmVzb3VyY2U9Im9yZy9qYWNvY28vYW50L2FudGxpYi54bWwiPgogICAgICAgIDxjbGFzc3BhdGggcGF0aD0iLi4vbGliL2phY29jb2FudC5qYXIiLz4KICAgIDwvdGFza2RlZj4KCiAgICA8cGF0aCBpZD0iamF2YWMuY2xhc3NwYXRoIj4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1jb21tb24vdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1kb2N1bWVudC90YXJnZXQvY2xhc3NlcyIvPgogICAgICAgIDxwYXRoZWxlbWVudCBsb2NhdGlvbj0ic2QtZHNzL2FwcHMvZHNzL2NvcmUvZHNzLXNlcnZpY2UvdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1zcGkvdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8ZmlsZXNldCBkaXI9Ii4uL2xpYiI+CiAgICAgICAgICAgIDxpbmNsdWRlIG5hbWU9IioqLyouamFyIi8+CiAgICAgICAgPC9maWxlc2V0PgogICAgPC9wYXRoPgoKICAgIDxwYXRoIGlkPSJydW4uY2xhc3NwYXRoIj4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249IiR7YnVpbGQuZGlyfSIvPgogICAgPC9wYXRoPgoKICAgIDx0YXJnZXQgbmFtZT0iamF2YWRvYyI+CiAgICAgICAgPGphdmFkb2MgcGFja2FnZW5hbWVzPSJvcmcuZGlnaWRvYzRqLmFwaSIgZGVzdGRpcj0iLi4vamF2YWRvYyI+CiAgICAgICAgICAgIDxzb3VyY2VwYXRoIHBhdGg9InNyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3MtY29tbW9uL3NyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3MtZG9jdW1lbnQvc3JjIi8+CiAgICAgICAgICAgIDxzb3VyY2VwYXRoIHBhdGg9InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1zZXJ2aWNlL3NyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3Mtc3BpL3NyYyIvPgogICAgICAgIDwvamF2YWRvYz4KICAgICAgICA8amFyIGRlc3RmaWxlPSIke2J1aWxkLmRpcn0vZGlnaWRvYzRqLSR7dmVyc2lvbn0tamF2YWRvYy5qYXIiIGJhc2VkaXI9Ii4uL2phdmFkb2MiLz4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iY29tcGlsZSI+CiAgICAgICAgPGphdmFjIGRlc3RkaXI9IiR7YnVpbGQuZGlyfSIgaW5jbHVkZWFudHJ1bnRpbWU9ImZhbHNlIiBkZWJ1Zz0ib24iIHNvdXJjZT0iMS43Ij4KICAgICAgICAgICAgPHNyYyBwYXRoPSJzcmMiLz4KICAgICAgICAgICAgPHNyYyBwYXRoPSJ0ZXN0Ii8+CiAgICAgICAgICAgIDxjbGFzc3BhdGggcmVmaWQ9ImphdmFjLmNsYXNzcGF0aCIvPgogICAgICAgIDwvamF2YWM+CiAgICA8L3RhcmdldD4KCiAgICA8dGFyZ2V0IG5hbWU9InNvdXJjZSI+CiAgICAgICAgPGphciBkZXN0ZmlsZT0iJHtidWlsZC5kaXJ9L2RpZ2lkb2M0ai0ke3ZlcnNpb259LXNvdXJjZXMuamFyIiBiYXNlZGlyPSIuLi9zcmMiLz4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iYWxsIiBkZXBlbmRzPSJjbGVhbiwgamF2YWRvYywgc291cmNlLCBjb21waWxlLCB0ZXN0LCBjb3ZlcmFnZS5yZXBvcnQiLz4KCiAgICA8dGFyZ2V0IG5hbWU9InRlc3QiIGRlcGVuZHM9ImNvbXBpbGUiPgogICAgICAgIDxqYWNvY286Y292ZXJhZ2U+CiAgICAgICAgICAgIDxqdW5pdCBmb3JrPSJ0cnVlIj4KICAgICAgICAgICAgICAgIDxjbGFzc3BhdGggcmVmaWQ9ImphdmFjLmNsYXNzcGF0aCIvPgogICAgICAgICAgICAgICAgPGNsYXNzcGF0aD4KICAgICAgICAgICAgICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249ImJ1aWxkIi8+CiAgICAgICAgICAgICAgICA8L2NsYXNzcGF0aD4KICAgICAgICAgICAgICAgIDxiYXRjaHRlc3Q+CiAgICAgICAgICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIke2J1aWxkLmRpcn0iIGluY2x1ZGVzPSIqKi8qVGVzdCouY2xhc3MiLz4KICAgICAgICAgICAgICAgIDwvYmF0Y2h0ZXN0PgogICAgICAgICAgICAgICAgPGZvcm1hdHRlciB0eXBlPSJicmllZiIgdXNlZmlsZT0iZmFsc2UiLz4KICAgICAgICAgICAgICAgIDxmb3JtYXR0ZXIgdHlwZT0ieG1sIi8+CiAgICAgICAgICAgIDwvanVuaXQ+CiAgICAgICAgPC9qYWNvY286Y292ZXJhZ2U+CiAgICAgICAgPGp1bml0cmVwb3J0IHRvZGlyPSIke2J1aWxkLmRpcn0iPgogICAgICAgICAgICA8ZmlsZXNldCBkaXI9Ii4uLyI+CiAgICAgICAgICAgICAgICA8aW5jbHVkZSBuYW1lPSJURVNULSoueG1sIi8+CiAgICAgICAgICAgIDwvZmlsZXNldD4KICAgICAgICAgICAgPHJlcG9ydCBmb3JtYXQ9Im5vZnJhbWVzIiB0b2Rpcj0iJHtidWlsZC5kaXJ9L2h0bWwiLz4KICAgICAgICA8L2p1bml0cmVwb3J0PgogICAgPC90YXJnZXQ+CgogICAgPHRhcmdldCBuYW1lPSJjb3ZlcmFnZS5yZXBvcnQiPgogICAgICAgIDxqYWNvY286cmVwb3J0PgogICAgICAgICAgICA8ZXhlY3V0aW9uZGF0YT4KICAgICAgICAgICAgICAgIDxmaWxlIGZpbGU9ImphY29jby5leGVjIi8+CiAgICAgICAgICAgIDwvZXhlY3V0aW9uZGF0YT4KCiAgICAgICAgICAgIDxzdHJ1Y3R1cmUgbmFtZT0iRXhhbXBsZSBQcm9qZWN0Ij4KICAgICAgICAgICAgICAgIDxjbGFzc2ZpbGVzPgogICAgICAgICAgICAgICAgICAgIDxmaWxlc2V0IGRpcj0iJHtidWlsZC5kaXJ9Ij4KICAgICAgICAgICAgICAgICAgICAgICAgPGV4Y2x1ZGUgbmFtZT0iKiovKlRlc3QqIi8+CiAgICAgICAgICAgICAgICAgICAgICAgIDxleGNsdWRlIG5hbWU9InByb3RvdHlwZS8iLz4KICAgICAgICAgICAgICAgICAgICAgICAgPGV4Y2x1ZGUgbmFtZT0ib3JnL2RpZ2lkb2M0ai9tYWluLyIvPgogICAgICAgICAgICAgICAgICAgIDwvZmlsZXNldD4KICAgICAgICAgICAgICAgIDwvY2xhc3NmaWxlcz4KICAgICAgICAgICAgICAgIDxzb3VyY2VmaWxlcyBlbmNvZGluZz0iVVRGLTgiPgogICAgICAgICAgICAgICAgICAgIDxmaWxlc2V0IGRpcj0ic3JjIi8+CiAgICAgICAgICAgICAgICA8L3NvdXJjZWZpbGVzPgogICAgICAgICAgICA8L3N0cnVjdHVyZT4KCiAgICAgICAgICAgIDxodG1sIGRlc3RkaXI9IiR7Y292ZXJhZ2UuZGlyfSIvPgogICAgICAgIDwvamFjb2NvOnJlcG9ydD4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iY2xlYW4iPgogICAgICAgIDxkZWxldGUgaW5jbHVkZWVtcHR5ZGlycz0idHJ1ZSIgZmFpbG9uZXJyb3I9ImZhbHNlIj4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIuLi8iIGluY2x1ZGVzPSJURVNUKi54bWwiLz4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIke2J1aWxkLmRpcn0iLz4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIuLi9qYXZhZG9jIi8+CiAgICAgICAgPC9kZWxldGU+CiAgICAgICAgPGRlbGV0ZSBmaWxlPSJ0ZXN0U2F2ZVRvRmlsZS50eHQiLz4KICAgICAgICA8bWtkaXIgZGlyPSIuLi9qYXZhZG9jIi8+CiAgICA8L3RhcmdldD4KCjwvcHJvamVjdD4=", dataFiles.get(0).getContent());
    }

    @Test
    void addDataFileButSignatureExists() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.addDataFiles(CONTAINER_ID, createDataFileListWithOneFile())
        );
        assertEquals("Unable to add/remove data file. Container contains signature(s)", caughtException.getMessage());
    }

    @Test
    void successfulAddDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain")).build();
        AsicContainerSession session = getContainerSession(container);

        container.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        List<DataFile> dataFiles = createDataFileListWithOneFile();
        dataFiles.get(0).setFileName("test.pdf");
        Result result = containerService.addDataFiles(CONTAINER_ID, dataFiles);
        assertEquals(Result.OK, result);
    }

    @Test
    void successfulRemoveDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test1.xml", "text/plain"))
                .withDataFile(new org.digidoc4j.DataFile("DxZzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZa".getBytes(), "test2.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.removeDataFile(CONTAINER_ID, "test1.xml");
        assertEquals(Result.OK, result);
    }

    @Test
    void removeDataFileNoDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml1", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        ResourceNotFoundException caughtException = assertThrows(
            ResourceNotFoundException.class, () -> {
                    Result result = containerService.removeDataFile(CONTAINER_ID, "test.xml");
                    assertEquals(Result.OK, result);
                }
        );
        assertEquals("Data file named test.xml not found", caughtException.getMessage());
    }

    @Test
    void successfulAugmentLtSignature() {
        Container container = createSignedContainer(SignatureProfile.LT);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.augmentSignatures(CONTAINER_ID);

        assertEquals(Result.OK, result);
        Mockito.verify(sessionService).update(sessionCaptor.capture());
        assertEquals(CONTAINER_SESSION_ID, sessionCaptor.getValue().getSessionId());
        AsicContainerSession sessionHolder = containerService.getSessionHolder(CONTAINER_ID);
        Container updatedContainer = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);
        assertEquals(1, updatedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, updatedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(updatedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
    }

    @Test
    void successfulAugmentLtaSignature() {
        Container container = createSignedContainer(SignatureProfile.LTA);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.augmentSignatures(CONTAINER_ID);

        assertEquals(Result.OK, result);
        Mockito.verify(sessionService).update(sessionCaptor.capture());
        assertEquals(CONTAINER_SESSION_ID, sessionCaptor.getValue().getSessionId());
        AsicContainerSession sessionHolder = containerService.getSessionHolder(CONTAINER_ID);
        Container updatedContainer = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);
        assertEquals(1, updatedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, updatedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(updatedContainer, 0);
        assertEquals(2, archiveTimestamps.size(), "The signature must contain 2 archive timestamps");
    }

    @Test
    void augmentContainerWithoutSignaturesThrows() {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> containerService.augmentSignatures(CONTAINER_ID)
        );

        assertEquals("Unable to augment. Container does not contain any signatures", caughtException.getMessage());
    }

    @Test
    void augmentContainerWithBLevelSignatureThrows() {
        Container container = createSignedContainer(SignatureProfile.B_BES);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> containerService.augmentSignatures(CONTAINER_ID)
        );

        assertEquals("Cannot augment signature profile B_BES", caughtException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"B_EPES", "LT_TM", "T"})
    void augmentContainerWithNotAugmentableSignatureThrows(String signatureProfile) throws URISyntaxException {
        String containerFilename = notAugmentableContainers.get(signatureProfile);
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(containerFilename).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> containerService.augmentSignatures(CONTAINER_ID)
        );

        assertEquals("Cannot augment signature profile " + signatureProfile, caughtException.getMessage());
    }

    @Test
    void successfulCloseSession() {
        String result = containerService.closeSession(CONTAINER_ID);
        assertEquals(Result.OK.name(), result);
    }

    @Test
    void uploadContainerWithDuplicateDataFilesThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("asice_duplicate_data_files.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer("test.asice", container)
        );
        assertEquals("Container contains duplicate data file: readme.txt", caughtException.getMessage());
    }

    @Test
    void uploadContainerWithDuplicateDataFileInManifestThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("asice_duplicate_data_files_in_manifest.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer("test.asice", container)
        );
        assertEquals("duplicate entry in manifest file: test.xml", caughtException.getMessage());
    }

    @Test
    void addDuplicateDataFileThrows() {
        Container container = ContainerBuilder
                .aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        container.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        List<DataFile> dataFiles = createDataFileListWithOneFile();
        dataFiles.get(0).setFileName("test.xml");

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.addDataFiles(CONTAINER_ID, dataFiles)
        );
        assertEquals("Duplicate data files not allowed: test.xml", caughtException.getMessage());
    }

    private byte[] getFile(String fileName) throws IOException, URISyntaxException {
        return TestUtil.getFileInputStream(fileName).readAllBytes();
    }

    private static AsicContainerSession getContainerSession(Container container) {
        Map<String, Integer> signatureIdHolder = new HashMap<>();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        AsicContainerSession session = AsicContainerSession.builder()
                .sessionId(CONTAINER_SESSION_ID)
                .clientName(CLIENT_NAME)
                .serviceName(SERVICE_NAME)
                .serviceUuid(SERVICE_UUID)
                .signatureIdHolder(signatureIdHolder)
                .containerName("test.asice")
                .container(outputStream.toByteArray())
                .build();
        return session;
    }

    private Container createSignedContainer(SignatureProfile profile) {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        SignatureBuilder builder = SignatureBuilder
                .aSignature(container)
                .withSignatureProfile(profile);
        org.digidoc4j.Signature signature = builder.withSignatureToken(pkcs12Esteid2018SignatureToken).invokeSigning();
        container.addSignature(signature);
        return container;
    }

    private List<TimestampToken> getSignatureArchiveTimestamps(Container container, int signatureIndex) {
        return ((AsicSignature) container.getSignatures().get(signatureIndex)).getOrigin().getDssSignature().getArchiveTimestamps();
    }
}
