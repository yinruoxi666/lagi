package ai.vector;

import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;

public class VectorStoreConstant {
    public static final String VECTOR_STORE_CHROMA = "Chroma";
    public static final String VECTOR_STORE_PINECONE = "Pinecone";
    public static final String VECTOR_METRIC_COSINE = "cosine";
    public static final int VECTOR_CACHE_SIZE = 10000;
    public static final boolean ENABLE_EXCEL_TO_MD;

    static {
        RAGFunction ragConfig = ContextLoader.configuration.getStores().getRag();
        ENABLE_EXCEL_TO_MD = ragConfig != null && ragConfig.isEnableExcelToMd();
    }

    public static class FileChunkSource {
        public static final String FILE_CHUNK_SOURCE_LLM = "llm";
        public static final String FILE_CHUNK_SOURCE_FILE = "file";
        public static final String FILE_CHUNK_SOURCE_QA = "qa";
    }

    public static class LogicalOperator {
        public static final String AND = "$and";
        public static final String OR = "$or";
    }

    public static class WhereOperator {
        public static final String GT = "$gt";    // greater than
        public static final String GTE = "$gte";  // greater than or equal
        public static final String LT = "$lt";    // less than
        public static final String LTE = "$lte";  // less than or equal
        public static final String NE = "$ne";    // not equal
        public static final String EQ = "$eq";    // equal
    }

    public static class InclusionExclusionOperator {
        public static final String IN = "$in";    // value in array
        public static final String NIN = "$nin";  // value not in array
    }

    public static class DocumentOperator {
        public static final String CONTAINS = "$contains";
        public static final String NOT_CONTAINS = "$not_contains";
    }

    public static class IncludeFields {
        public static final String METADATAS = "metadatas";
        public static final String DOCUMENTS = "documents";
        public static final String EMBEDDINGS = "embeddings";
        public static final String DISTANCES = "distances";
    }
}