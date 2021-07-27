package htsjdk.beta.codecs.variants.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.beta.codecs.variants.vcf.vcfv3_2.VCFCodecV3_2;
import htsjdk.beta.codecs.variants.vcf.vcfv3_3.VCFCodecV3_3;
import htsjdk.beta.codecs.variants.vcf.vcfv4_0.VCFCodecV4_0;
import htsjdk.beta.codecs.variants.vcf.vcfv4_1.VCFCodecV4_1;
import htsjdk.beta.codecs.variants.vcf.vcfv4_2.VCFCodecV4_2;
import htsjdk.beta.codecs.variants.vcf.vcfv4_3.VCFCodecV4_3;
import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.IOUtils;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.Function;

public class HtsVCFCodecTest extends HtsjdkTest {
    final IOPath VARIANTS_TEST_DIR = new HtsPath("src/test/resources/htsjdk/");
    final IOPath TEST_VCF_WITH_INDEX = new HtsPath("src/test/resources/htsjdk/variant/HiSeq.10000.vcf.bgz");
    final IOPath TEST_VCF_INDEX = new HtsPath("src/test/resources/htsjdk/variant/HiSeq.10000.vcf.bgz.tbi");

    @DataProvider(name="vcfReadWriteTests")
    private Object[][] vcfReadWriteTests() {
        return new Object[][] {
                // one test case for each supported VCF version
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcfexampleV3.2.vcf"), VCFCodecV3_2.VCF_V32_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "tribble/tabix/trioDup.vcf"), VCFCodecV3_3.VCF_V33_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/HiSeq.10000.vcf"), VCFCodecV4_0.VCF_V40_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/dbsnp_135.b37.1000.vcf"), VCFCodecV4_1.VCF_V41_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/vcf42HeaderLines.vcf"), VCFCodecV4_2.VCF_V42_VERSION },
                { new HtsPath(VARIANTS_TEST_DIR + "variant/NA12891.vcf.gz"), VCFCodecV4_2.VCF_V42_VERSION },
                // v4.3 is left out since these tests write to the newest writeable VCF version (4.2), but we can't
                // write a header from a v4.3 source since it will (correctly) be rejected by the v4.2 writer
        };
    }

    @Test(dataProvider = "vcfReadWriteTests")
    public void testRoundTripVCF(final IOPath inputPath, final HtsVersion expectedCodecVersion) {
        readWriteVCF(inputPath, expectedCodecVersion);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectWritingV43HeaderAsV42()  {
        // read vcf v4.3 and try to write it to a vcf v4.2 (header is rejected)
        readWriteVCF(new HtsPath(VARIANTS_TEST_DIR + "variant/vcf43/all43Features.vcf"), VCFCodecV4_3.VCF_V43_VERSION);
    }

    @DataProvider(name="queryMethodsCases")
    private Object[][] queryMethodsCases() {
        return new Object[][] {
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.queryStart("chr1", 177) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.query("chr1", 177, 178,
                        HtsQueryRule.OVERLAPPING) },
                { (Function<VariantsDecoder, ?>) (VariantsDecoder vcfDecoder) -> vcfDecoder.queryOverlapping("chr1", 177, 178) },
        };
    }

    @Test(dataProvider="queryMethodsCases")
    public void testAcceptIndexInBundle(final Function<VariantsDecoder, ?> queryFunction) {
        // use a vcf that is known to have an on-disk companion index to ensure that attempts to make
        // index queries are rejected if the index is not explicitly included in the input bundle
        final Bundle variantsBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(TEST_VCF_WITH_INDEX, BundleResourceType.VARIANT_CONTEXTS))
                .addSecondary(new IOPathResource(TEST_VCF_INDEX, BundleResourceType.VARIANTS_INDEX))
                .build();

        try (final VariantsDecoder variantsDecoder =
                HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(variantsBundle)) {
            Assert.assertTrue(variantsDecoder.hasIndex());
            Assert.assertTrue(variantsDecoder.isQueryable());
            queryFunction.apply(variantsDecoder);
        }
    }

    @Test(dataProvider="queryMethodsCases", expectedExceptions = IllegalArgumentException.class)
    public void testRejectIndexNotIncludedInBundle(final Function<VariantsDecoder, ?> queryFunction) {
        // use a bam that is known to have an on-disk companion index to ensure that attempts to make
        // index queries are rejected if the index is not explicitly included in the input bundle
        final Bundle variantsBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(TEST_VCF_WITH_INDEX, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        Assert.assertFalse(variantsBundle.get(BundleResourceType.VARIANTS_INDEX).isPresent());

        try (final VariantsDecoder variantsDecoder =
                     HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(variantsBundle)) {

            Assert.assertFalse(variantsDecoder.hasIndex());
            Assert.assertFalse(variantsDecoder.isQueryable());

            // now try every possible query method
            queryFunction.apply(variantsDecoder);
        }
    }

//    @Test
//    public void testSpecificQueries() {
//        assertEquals(runQueryTest(TEST_BAM, "chrM", 10400, 10600, HtsQueryRule.CONTAINED), 1);
//        assertEquals(runQueryTest(TEST_BAM, "chrM", 10400, 10600, HtsQueryRule.OVERLAPPING), 2);
//    }

    private void readWriteVCF(final IOPath inputPath, final HtsVersion expectedCodecVersion) {
        final IOPath outputPath = IOUtils.createTempPath("pluginVariants", ".vcf");

        // some test files require "AllowMissingFields" options for writing
        final VariantsEncoderOptions variantsEncoderOptions = new VariantsEncoderOptions().setAllowFieldsMissingFromHeader(true);
        try (final VariantsDecoder variantsDecoder = HtsDefaultRegistry.getVariantsResolver().getVariantsDecoder(inputPath);
             final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                     outputPath,
                     variantsEncoderOptions)) {

            Assert.assertNotNull(variantsDecoder);
            Assert.assertEquals(variantsDecoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsDecoder.getVersion(), expectedCodecVersion);
            Assert.assertTrue(variantsDecoder.getDisplayName().contains(inputPath.toString()));

            Assert.assertNotNull(variantsEncoder);
            Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_2.VCF_V42_VERSION);
            Assert.assertTrue(variantsEncoder.getDisplayName().contains(outputPath.toString()));

            final VCFHeader vcfHeader = variantsDecoder.getHeader();
            Assert.assertNotNull(vcfHeader);

            variantsEncoder.setHeader(vcfHeader);
            for (final VariantContext vc : variantsDecoder) {
                variantsEncoder.write(vc);
            }
        }
    }

    @Test
    public void testGetDecoderForFormatAndVersion() {
        final IOPath tempOutputPath = IOPathUtils.createTempPath("testGetDecoderForFormatAndVersion", ".vcf");
        final Bundle outputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(tempOutputPath, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        try (final VariantsEncoder variantsEncoder = HtsDefaultRegistry.getVariantsResolver().getVariantsEncoder(
                outputBundle,
                new VariantsEncoderOptions(),
                VariantsFormats.VCF,
                VCFCodecV4_2.VCF_V42_VERSION)) {
            Assert.assertEquals(variantsEncoder.getFileFormat(), VariantsFormats.VCF);
            Assert.assertEquals(variantsEncoder.getVersion(), VCFCodecV4_2.VCF_V42_VERSION);
        }
    }
}
