package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.build.CompressionHeaderFactory;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.SequenceUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by vadim on 07/12/2015.
 */
public class SliceTests extends HtsjdkTest {
    private static final int TEST_RECORD_COUNT = 10;
    private static final int READ_LENGTH_FOR_TEST_RECORDS = CRAMStructureTestUtil.READ_LENGTH_FOR_TEST_RECORDS;

    @Test
    public void testUnmappedValidateRef() {
        final Slice slice = new Slice(ReferenceContext.UNMAPPED_UNPLACED_CONTEXT);

        Assert.assertTrue(slice.validateRefMD5(null));
        Assert.assertTrue(slice.validateRefMD5(new byte[0]));
        Assert.assertTrue(slice.validateRefMD5(new byte[1024]));
    }

    @Test
    public void test_validateRef() {
        byte[] ref = "AAAAA".getBytes();
        final byte[] md5 = SequenceUtil.calculateMD5(ref, 0, Math.min(5, ref.length));
        final Slice slice = new Slice(new ReferenceContext(0));
        slice.setAlignmentSpan(5);
        slice.setAlignmentStart(1);
        slice.setRefMD5(ref);

        Assert.assertEquals(slice.getRefMD5(), md5);
        Assert.assertTrue(slice.validateRefMD5(ref));
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testFailsMD5Check() throws IOException {
        // auxf.alteredForMD5test.fa has been altered slightly from the original reference
        // to cause the CRAM md5 check to fail
        final File CRAMFile = new File("src/test/resources/htsjdk/samtools/cram/auxf#values.3.0.cram");
        final File refFile = new File("src/test/resources/htsjdk/samtools/cram/auxf.alteredForMD5test.fa");
        ReferenceSource refSource = new ReferenceSource(refFile);
        CRAMFileReader reader = null;
        try {
            reader = new CRAMFileReader(
                    CRAMFile,
                    null,
                    refSource,
                    ValidationStringency.STRICT);
            Iterator<SAMRecord> it = reader.getIterator();
            while (it.hasNext()) {
                it.next();
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @DataProvider(name = "sliceStateTestCases")
    private Object[][] sliceStateTestCases() {
        final int mappedSequenceId = 0;  // arbitrary
        final ReferenceContext mappedRefContext = new ReferenceContext(mappedSequenceId);
        final List<Object[]> retval = new ArrayList<>();
        final boolean[] coordinateSorteds = new boolean[] { true, false };
        for (final boolean coordSorted : coordinateSorteds) {
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getSingleRefRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        coordSorted,
                        mappedRefContext, 1,
                        READ_LENGTH_FOR_TEST_RECORDS + TEST_RECORD_COUNT - 1
                });
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getMultiRefRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN
                });
            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getUnplacedRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN
                });


                // these two sets of records are "half" unplaced: they have either a valid reference index or start position,
                // but not both.  We treat these weird edge cases as unplaced.

            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoRefRecords(TEST_RECORD_COUNT),
                        coordSorted,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN
                });

            retval.add(new Object[]
                {
                        CRAMStructureTestUtil.getHalfUnplacedNoStartRecords(TEST_RECORD_COUNT, mappedSequenceId),
                        coordSorted,
                        ReferenceContext.UNMAPPED_UNPLACED_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN
                });
        }

        return retval.toArray(new Object[0][0]);
    }

    @Test(dataProvider = "sliceStateTestCases")
    public void sliceStateTest(final List<CRAMRecord> records,
                               final boolean coordinateSorted,
                               final ReferenceContext expectedReferenceContext,
                               final int expectedAlignmentStart,
                               final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, coordinateSorted);
        final Slice slice = new Slice(records, header, 0L);
        final int expectedBaseCount = TEST_RECORD_COUNT * READ_LENGTH_FOR_TEST_RECORDS;
        CRAMStructureTestUtil.assertSliceState(slice, expectedReferenceContext,
                expectedAlignmentStart, expectedAlignmentSpan, TEST_RECORD_COUNT, expectedBaseCount);
    }

    // show that a slice with a single ref will initially be built as single-ref
    // but adding an additional ref will make it multiref
    // and more will keep it multiref (mapped or otherwise)

    @Test
    public void testBuildStates() {
        final List<CRAMRecord> records = new ArrayList<>();

        int index = 0;
        final int alignmentStart = 100;  // arbitrary
        final CRAMRecord record1 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record1);
        buildSliceAndAssert(records, new ReferenceContext(index), alignmentStart, READ_LENGTH_FOR_TEST_RECORDS);

        index++;
        final CRAMRecord record2 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record2);
        buildSliceAndAssert(records, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN);

        index++;
        final CRAMRecord record3 = CRAMStructureTestUtil.createMappedRecord(index, index, alignmentStart);
        records.add(record3);
        buildSliceAndAssert(records, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN);

        index++;
        final CRAMRecord unmapped = CRAMStructureTestUtil.createUnmappedUnplacedRecord(index);
        records.add(unmapped);
        buildSliceAndAssert(records, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT, AlignmentContext.NO_ALIGNMENT_START, AlignmentContext.NO_ALIGNMENT_SPAN);
    }

//    @Test
//    public void testSingleAndUnmappedBuild() {
//        final List<CRAMRecord> records = new ArrayList<>();
//
//        final int mappedSequenceId = 0;  // arbitrary
//        final int alignmentStart = 10;  // arbitrary
//        int index = 0;
//        final CRAMRecord single = CRAMStructureTestUtil.createMappedRecord(index, mappedSequenceId, alignmentStart);
//        single.readLength = 20;
//        records.add(single);
//
//        index++;
//        final CRAMRecord unmapped = CRAMStructureTestUtil.createUnmappedUnplacedRecord(index);
//        unmapped.readLength = 35;
//        records.add(unmapped);
//
//        final CompressionHeader header = new CompressionHeaderFactory().build(records, true);
//
//        final Slice slice = Slice.buildSlice(records, header);
//        final int expectedBaseCount = single.getReadLength() + unmapped.getReadLength();
//        CRAMStructureTestUtil.assertSliceState(slice, ReferenceContext.MULTIPLE_REFERENCE_CONTEXT,
//                Slice.NO_ALIGNMENT_START, Slice.NO_ALIGNMENT_SPAN, records.size(), expectedBaseCount);
//    }

    @Test(dataProvider = "uninitializedBAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
    public void uninitializedBAIParameterTest(final Slice s) {
        s.baiIndexInitializationCheck();
    }

    @Test(dataProvider = "uninitializedCRAIParameterTestCases", dataProviderClass = CRAMStructureTestUtil.class, expectedExceptions = CRAMException.class)
    public void uninitializedCRAIParameterTest(final Slice s) {
        s.craiIndexInitializationCheck();
    }

    private static void buildSliceAndAssert(final List<CRAMRecord> records,
                                            final ReferenceContext expectedReferenceContext,
                                            final int expectedAlignmentStart,
                                            final int expectedAlignmentSpan) {
        final CompressionHeader header = new CompressionHeaderFactory().build(records, true);
        final Slice slice = new Slice(records, header, 0L);
        final int expectedBaseCount = records.size() * READ_LENGTH_FOR_TEST_RECORDS;
        CRAMStructureTestUtil.assertSliceState(slice, expectedReferenceContext,
                expectedAlignmentStart, expectedAlignmentSpan, records.size(), expectedBaseCount);
    }

    // Embedded reference block tests

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockNoContentID() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();

        // this test is a little bogus in that, per the spec, it shouldn't even be possible to create an external block
        // with contentID=0 in the first place, but we allow it due to  https://github.com/samtools/htsjdk/issues/1232,
        // and because we have lots of CRAM files floating around that were generated this way
        final Block block = Block.createExternalBlock(
                BlockCompressionMethod.GZIP,
                Slice.EMBEDDED_REFERENCE_ABSENT_CONTENT_ID,
                new byte[2],
                2);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectNonExternalEmbeddedReferenceBlock() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();
        final Block block = Block.createRawCoreDataBlock(new byte[2]);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectEmbeddedReferenceBlockConflictsWithID() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();
        final int embeddedReferenceBlockContentID = 27;
        slice.setEmbeddedReferenceContentID(embeddedReferenceBlockContentID);
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, embeddedReferenceBlockContentID + 1, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = CRAMException.class)
    public void testRejectResetEmbeddedReferenceBlock() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        slice.setEmbeddedReferenceBlock(block);
        slice.setEmbeddedReferenceBlock(block);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectResetEmbeddedReferenceBlockContentID() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();
        slice.setEmbeddedReferenceContentID(27);
        slice.setEmbeddedReferenceContentID(28);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRejectConflictingEmbeddedReferenceBlockContentID() {
        final Slice slice = SliceTestHelper.getSingleRecordSlice();
        final Block block = Block.createExternalBlock(BlockCompressionMethod.GZIP, 27, new byte[2], 2);
        slice.setEmbeddedReferenceContentID(28);
        slice.setEmbeddedReferenceBlock(block);
    }

}
