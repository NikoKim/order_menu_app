class LatestRequest {
  int _generation = 0;

  int begin() => ++_generation;

  bool isLatest(int generation) => generation == _generation;
}
