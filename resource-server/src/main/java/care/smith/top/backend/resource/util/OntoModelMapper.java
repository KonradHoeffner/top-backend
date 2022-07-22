package care.smith.top.backend.resource.util;

import care.smith.top.backend.model.Constant;
import care.smith.top.backend.model.ExpressionFunction;
import care.smith.top.simple_onto_api.calculator.functions.Function;

public class OntoModelMapper {
  public static ExpressionFunction map(Function function) {
    return new ExpressionFunction()
        .id(function.getId())
        .title(function.getTitle())
        .notation(map(function.getNotation()))
        .minArgumentNumber(function.getMinArgumentsNumber())
        .maxArgumentNumber(function.getMaxArgumentsNumber());
  }

  public static ExpressionFunction.NotationEnum map(Function.Notation notation) {
    return ExpressionFunction.NotationEnum.valueOf(notation.name());
  }

  public static Constant map(
      care.smith.top.simple_onto_api.calculator.constants.Constant constant) {
    return new Constant().id(constant.getId()).title(constant.getTitle());
  }
}
