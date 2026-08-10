# Hybrid Search Mock Demo

This demo executes the same `HybridMetadataSearchEngine` used by the production
`POST /v1/vector/hybridSearchByMetadata` endpoint with deterministic in-memory
mock data.

Run it from the repository root:

```bash
bash tools/hybrid-search-demo/run.sh
```

The runner compiles only the hybrid-search production classes, so it can be
used on a workstation where the legacy application as a whole requires an
older JDK. It performs assertions before printing a successful JSON response.

The response demonstrates:

- weighted RRF `hybrid_score`;
- query keyword TF/DF scores;
- document BM25 score, rank, and matched keywords;
- embedding distance and dense rank;
- rerank rank and score;
- Elasticsearch backend and fallback/status fields.

Any missing required field or incorrect pipeline result terminates the demo
with a non-zero exit code.
