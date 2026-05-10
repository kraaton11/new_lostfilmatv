from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class TranslationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    text: str = Field(min_length=1, max_length=5000)
    source_language: str = Field(
        default="EN",
        validation_alias=AliasChoices("source_language", "sourceLanguage"),
    )
    target_language: str = Field(
        default="RU",
        validation_alias=AliasChoices("target_language", "targetLanguage"),
    )


class TranslationResponse(BaseModel):
    text: str
    source_language: str
    target_language: str
    provider: str
